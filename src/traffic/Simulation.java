package traffic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** Headless deterministic model. All mutations belong to the simulation thread. */
public final class Simulation {
  public static final double DT = .02,
      SPEED_LIMIT = 50 / 3.6,
      CAR_LENGTH = 4.5,
      CAR_WIDTH = 1.8,
      GAP = 2;
  public static final int MAX_ACTIVE = 240;
  public final RoadNetwork network;
  public final IntersectionController controller = new IntersectionController();
  final List<Vehicle> vehicles = new ArrayList<>();
  final DemandProfile demand = new DemandProfile();
  final Metrics metrics = new Metrics();
  final boolean[] blockedExits = new boolean[4];
  private Random random;
  private long seed, nextId;
  private long ticks;
  private long generated;

  public Simulation(long seed) {
    this(new RoadNetwork(), seed);
  }

  public Simulation(RoadNetwork network, long seed) {
    this.network = network;
    reset(seed);
  }

  public double time() {
    return ticks * DT;
  }

  public long seed() {
    return seed;
  }

  public void reset(long seed) {
    this.seed = seed;
    random = new Random(seed);
    nextId = 1;
    ticks = 0;
    generated = 0;
    vehicles.clear();
    metrics.clear();
    controller.reset();
    for (int i = 0; i < 4; i++) {
      demand.queues[i].clear();
      demand.remainingHazard[i] = exponential();
      blockedExits[i] = false;
    }
  }

  private double exponential() {
    return -Math.log(1 - random.nextDouble());
  }

  public void setRate(RoadNetwork.Arm arm, int rate) {
    if (rate < 0 || rate > 1200) throw new IllegalArgumentException("Arrival rate must be 0–1200");
    demand.rates[arm.ordinal()] = rate;
  }

  public void preset(String name) {
    int[] rates =
        "Main-road rush".equals(name)
            ? new int[] {1000, 250, 1000, 250}
            : "Heavy traffic".equals(name)
                ? new int[] {1200, 1200, 1200, 1200}
                : new int[] {300, 300, 300, 300};
    System.arraycopy(rates, 0, demand.rates, 0, 4);
  }

  public void step() {
    ticks++;
    controller.step(DT, occupied());
    for (RoadNetwork.Arm arm : RoadNetwork.Arm.values()) {
      int i = arm.ordinal();
      demand.remainingHazard[i] -= demand.rates[i] / 3600.0 * DT;
      while (demand.remainingHazard[i] <= 0) {
        double roll = random.nextDouble();
        RoadNetwork.Turn turn =
            roll < .6
                ? RoadNetwork.Turn.STRAIGHT
                : roll < .8 ? RoadNetwork.Turn.LEFT : RoadNetwork.Turn.RIGHT;
        demand.queues[i].addLast(
            new Arrival(nextId++, time(), network.route(arm, turn), random.nextInt(8)));
        generated++;
        demand.remainingHazard[i] += exponential();
      }
      if (!demand.queues[i].isEmpty() && vehicles.size() < MAX_ACTIVE && spawnSpace(arm)) {
        Arrival arrival = demand.queues[i].removeFirst();
        Vehicle vehicle = new Vehicle(arrival);
        vehicle.waiting = time() - arrival.time;
        vehicles.add(vehicle);
      }
    }
    releaseReservations();
    List<Vehicle> candidates = new ArrayList<>();
    for (Vehicle v : vehicles) {
      if (!v.admitted && !v.cleared && v.distance >= RoadNetwork.INBOUND - 18 && isFront(v)) {
        if (Double.isNaN(v.stopArrival)) v.stopArrival = time();
        candidates.add(v);
      }
    }
    candidates.sort(
        Comparator.comparingInt((Vehicle v) -> priority(v))
            .thenComparingDouble(v -> v.stopArrival)
            .thenComparingLong(v -> v.id));
    for (Vehicle v : candidates) if (canAdmit(v, candidates)) v.admitted = true;
    for (Vehicle v : vehicles) move(v);
    for (int i = vehicles.size() - 1; i >= 0; i--) {
      Vehicle v = vehicles.get(i);
      if (v.distance >= v.route.length + CAR_LENGTH / 2) {
        metrics.complete(time(), Math.max(0, time() - v.arrivalTime - v.route.freeFlowTime));
        vehicles.remove(i);
      }
    }
    metrics.expire(time());
  }

  private int priority(Vehicle v) {
    int major =
        controller.mode() == IntersectionController.Mode.GIVE_WAY && !v.route.origin.northSouth()
            ? 2
            : 0;
    return major + (v.route.turn == RoadNetwork.Turn.RIGHT ? 1 : 0);
  }

  private boolean spawnSpace(RoadNetwork.Arm arm) {
    for (Vehicle v : vehicles)
      if (v.route.origin == arm && v.distance < CAR_LENGTH + GAP + 1) return false;
    return true;
  }

  private boolean isFront(Vehicle v) {
    for (Vehicle other : vehicles)
      if (other != v
          && other.route.origin == v.route.origin
          && other.distance > v.distance
          && other.distance < RoadNetwork.INBOUND + CAR_LENGTH
          && !(other.route == v.route && other.admitted)) return false;
    return true;
  }

  private boolean canAdmit(Vehicle v, List<Vehicle> candidates) {
    if (!controller.allows(v.route.origin) || !exitSpace(v.route)) return false;
    for (Vehicle other : vehicles)
      if (other != v && other.admitted && network.conflicts(v.route, other.route)) return false;
    for (Vehicle other : candidates) {
      if (other == v || !controller.allows(other.route.origin) || !exitSpace(other.route)) continue;
      if (network.conflicts(v.route, other.route) && priority(other) < priority(v)) return false;
    }
    // On the minor road, accept a gap only when conflicting major-road traffic is >3 seconds away.
    if (controller.mode() == IntersectionController.Mode.GIVE_WAY && !v.route.origin.northSouth()) {
      for (Vehicle other : vehicles)
        if (other.route.origin.northSouth()
            && !other.admitted
            && other.distance < RoadNetwork.INBOUND
            && network.conflicts(v.route, other.route)
            && (RoadNetwork.INBOUND - other.distance) / Math.max(2, other.speed) < 3) return false;
    }
    return true;
  }

  private boolean exitSpace(RoadNetwork.MovementRoute route) {
    if (blockedExits[route.destination.ordinal()]) return false;
    for (Vehicle other : vehicles)
      if (other.route.destination == route.destination) {
        double outgoing = other.distance - other.route.exitDistance;
        if (outgoing >= 0 && outgoing < CAR_LENGTH + GAP + 2) return false;
      }
    return true;
  }

  private void releaseReservations() {
    for (Vehicle v : vehicles)
      if (v.admitted && v.distance > v.route.exitDistance + CAR_LENGTH / 2 + 1) {
        v.admitted = false;
        v.cleared = true;
      }
  }

  public boolean occupied() {
    for (Vehicle v : vehicles) if (v.admitted) return true;
    return false;
  }

  private void move(Vehicle v) {
    v.previousDistance = v.distance;
    double desired = SPEED_LIMIT;
    if (v.distance > RoadNetwork.INBOUND - 20 && v.distance < v.route.exitDistance + 5)
      desired = v.route.turnSpeed();
    double gap = Double.POSITIVE_INFINITY, leadSpeed = 0;
    if (!v.admitted && !v.cleared) gap = RoadNetwork.INBOUND + GAP - CAR_LENGTH / 2 - v.distance;
    if (blockedExits[v.route.destination.ordinal()] && v.distance > v.route.exitDistance)
      gap = Math.min(gap, v.route.length - 8 - v.distance);
    for (Vehicle other : vehicles) {
      if (other == v) continue;
      double separation = Double.POSITIVE_INFINITY;
      if (v.route == other.route && other.distance > v.distance)
        separation = other.distance - v.distance - CAR_LENGTH;
      else if (v.route.origin == other.route.origin
          && v.distance <= RoadNetwork.INBOUND
          && other.distance > v.distance
          && other.distance < RoadNetwork.INBOUND + CAR_LENGTH)
        separation = other.distance - v.distance - CAR_LENGTH;
      else if (v.route.destination == other.route.destination
          && v.distance >= v.route.exitDistance
          && other.distance >= other.route.exitDistance) {
        double delta =
            (other.distance - other.route.exitDistance) - (v.distance - v.route.exitDistance);
        if (delta > 0) separation = delta - CAR_LENGTH;
      }
      if (separation < gap) {
        gap = separation;
        leadSpeed = other.speed;
      }
    }
    double interaction = 0;
    if (Double.isFinite(gap)) {
      double target =
          GAP
              + Math.max(
                  0, v.speed * 1.5 + v.speed * (v.speed - leadSpeed) / (2 * Math.sqrt(2 * 3)));
      interaction = Math.pow(target / Math.max(.1, gap), 2);
    }
    double acceleration = 2 * (1 - Math.pow(v.speed / desired, 4) - interaction);
    acceleration = Math.max(-4.5, Math.min(2, acceleration));
    double nextSpeed = Math.max(0, v.speed + acceleration * DT);
    double travel = (v.speed + nextSpeed) * .5 * DT;
    if (Double.isFinite(gap)) {
      double allowed = Math.max(0, gap - GAP);
      if (travel > allowed) {
        travel = allowed;
        nextSpeed = Math.min(nextSpeed, travel / DT);
      }
    }
    v.distance += travel;
    v.braking = nextSpeed < v.speed - .015;
    v.speed = nextSpeed;
    if (v.speed < .5) v.waiting += DT;
  }

  public SimulationSnapshot snapshot() {
    List<SimulationSnapshot.Car> cars = new ArrayList<>();
    int[] queues = new int[4], backlog = new int[4];
    for (Vehicle v : vehicles) {
      cars.add(new SimulationSnapshot.Car(v));
      if (v.distance < RoadNetwork.INBOUND && v.speed < .5) queues[v.route.origin.ordinal()]++;
    }
    String[] signals = new String[4];
    for (RoadNetwork.Arm arm : RoadNetwork.Arm.values()) {
      backlog[arm.ordinal()] = demand.queues[arm.ordinal()].size();
      signals[arm.ordinal()] = controller.signal(arm);
    }
    return new SimulationSnapshot(
        time(),
        seed,
        cars,
        queues,
        backlog,
        demand.rates,
        signals,
        metrics.completed,
        time() >= 60 ? metrics.recent.size() : Double.NaN,
        metrics.completed == 0 ? Double.NaN : metrics.delaySum / metrics.completed,
        controller.mode(),
        controller.requestedMode(),
        controller.phase(),
        controller.remaining(),
        generated,
        controller.pendingNs(),
        controller.pendingEw());
  }
  // Scripted scenarios use these package-level seams without real-time scheduling or UI.
  Vehicle inject(RoadNetwork.Arm arm, RoadNetwork.Turn turn, double distance, double speed) {
    Vehicle v = new Vehicle(new Arrival(nextId++, time(), network.route(arm, turn), 0));
    v.distance = distance;
    v.previousDistance = distance;
    v.speed = speed;
    vehicles.add(v);
    generated++;
    return v;
  }

  void blockExit(RoadNetwork.Arm arm, boolean blocked) {
    blockedExits[arm.ordinal()] = blocked;
  }

  static final class Arrival {
    final long id;
    final double time;
    final RoadNetwork.MovementRoute route;
    final int color;

    Arrival(long id, double time, RoadNetwork.MovementRoute route, int color) {
      this.id = id;
      this.time = time;
      this.route = route;
      this.color = color;
    }
  }

  static final class Vehicle {
    final long id;
    final double arrivalTime;
    final RoadNetwork.MovementRoute route;
    final int color;
    double distance, previousDistance, speed, waiting, stopArrival = Double.NaN;
    boolean admitted, cleared, braking;

    Vehicle(Arrival a) {
      id = a.id;
      arrivalTime = a.time;
      route = a.route;
      color = a.color;
    }
  }

  static final class DemandProfile {
    final int[] rates = {300, 300, 300, 300};
    final double[] remainingHazard = new double[4];

    @SuppressWarnings("unchecked")
    final Deque<Arrival>[] queues =
        new Deque[] {
          new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>()
        };
  }

  static final class Metrics {
    long completed;
    double delaySum;
    final Deque<Double> recent = new ArrayDeque<>();

    void complete(double time, double delay) {
      completed++;
      delaySum += delay;
      recent.addLast(time);
    }

    void expire(double time) {
      while (!recent.isEmpty() && recent.peekFirst() <= time - 60) recent.removeFirst();
    }

    void clear() {
      completed = 0;
      delaySum = 0;
      recent.clear();
    }
  }
}
