package traffic.engine;

import traffic.model.RoadNetwork;
import traffic.model.VehicleParameters;
import traffic.signals.DetectorSnapshot;
import traffic.signals.IntersectionController;
import traffic.signals.VehicleDetector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Headless deterministic model. All mutations belong to the simulation thread. */
public final class Simulation {
    public static final double DT = VehicleParameters.DT;
    public static final double SPEED_LIMIT = VehicleParameters.SPEED_LIMIT;
    public static final double CAR_LENGTH = VehicleParameters.CAR_LENGTH;
    public static final double CAR_WIDTH = VehicleParameters.CAR_WIDTH;
    public static final double GAP = VehicleParameters.GAP;
    public static final int MAX_ACTIVE = 240;
    public final RoadNetwork network;
    public final IntersectionController controller = new IntersectionController();
    final List<Vehicle> vehicles = new ArrayList<>();
    final DemandProfile demand = new DemandProfile();
    final Metrics metrics = new Metrics();
    final boolean[] blockedExits = new boolean[4];
    private final VehicleDynamics dynamics = new VehicleDynamics();
    private final VehicleDetector detector = new VehicleDetector();
    private final JunctionAdmission admission;
    private DetectorSnapshot detection = DetectorSnapshot.empty();
    private Random random;
    private long seed;
    private long nextId;
    private long ticks;
    private long generated;

    public Simulation(long seed) {
        this(new RoadNetwork(), seed);
    }

    public Simulation(RoadNetwork network, long seed) {
        this.network = network;
        admission = new JunctionAdmission(network, controller, vehicles, blockedExits);
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
        detection = DetectorSnapshot.empty();
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
        if (rate < 0 || rate > 1200) {
            throw new IllegalArgumentException("Arrival rate must be 0–1200");
        }
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
        detection = detectVehicles();
        controller.step(DT, occupied(), detection);
        generateArrivals();
        spawnQueuedVehicles();
        releaseReservations();
        admitWaitingVehicles();
        for (Vehicle vehicle : vehicles) {
            dynamics.move(vehicle, vehicles, blockedExits);
        }
        completeTrips();
        metrics.expire(time());
    }

    private void generateArrivals() {
        long previousId = nextId;
        nextId = demand.generateArrivals(random, network, time(), nextId);
        generated += nextId - previousId;
    }

    private void spawnQueuedVehicles() {
        for (RoadNetwork.Arm arm : RoadNetwork.Arm.values()) {
            int i = arm.ordinal();
            if (!demand.queues[i].isEmpty()
                    && vehicles.size() < MAX_ACTIVE
                    && admission.spawnSpace(arm)) {
                Arrival arrival = demand.queues[i].removeFirst();
                Vehicle vehicle = new Vehicle(arrival);
                vehicle.waiting = time() - arrival.time;
                vehicles.add(vehicle);
            }
        }
    }

    private void admitWaitingVehicles() {
        List<Vehicle> candidates = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (!vehicle.admitted
                    && !vehicle.cleared
                    && vehicle.distance >= RoadNetwork.INBOUND - 18
                    && admission.isFront(vehicle)) {
                if (Double.isNaN(vehicle.stopArrival)) {
                    vehicle.stopArrival = time();
                }
                candidates.add(vehicle);
            }
        }
        candidates.sort(
                Comparator.comparingInt((Vehicle vehicle) -> admission.priority(vehicle))
                        .thenComparingDouble(vehicle -> vehicle.stopArrival)
                        .thenComparingLong(vehicle -> vehicle.id));
        for (Vehicle vehicle : candidates)
            if (admission.canAdmit(vehicle, candidates)) {
                vehicle.admitted = true;
            }
    }

    private void completeTrips() {
        for (int i = vehicles.size() - 1; i >= 0; i--) {
            Vehicle vehicle = vehicles.get(i);
            if (vehicle.distance >= vehicle.route.length + CAR_LENGTH / 2) {
                metrics.complete(
                        time(),
                        Math.max(0, time() - vehicle.arrivalTime - vehicle.route.freeFlowTime));
                vehicles.remove(i);
            }
        }
    }

    private void releaseReservations() {
        for (Vehicle vehicle : vehicles)
            if (vehicle.admitted
                    && vehicle.distance > vehicle.route.exitDistance + CAR_LENGTH / 2 + 1) {
                vehicle.admitted = false;
                vehicle.cleared = true;
            }
    }

    public boolean occupied() {
        for (Vehicle vehicle : vehicles)
            if (vehicle.admitted) {
                return true;
            }
        return false;
    }

    private DetectorSnapshot detectVehicles() {
        boolean[] stopLine = new boolean[4];
        boolean[] advance = new boolean[4];

        for (Vehicle vehicle : vehicles) {
            if (vehicle.cleared || vehicle.distance > RoadNetwork.INBOUND + CAR_LENGTH / 2) {
                continue;
            }
            int approach = vehicle.route.origin.ordinal();
            double front = vehicle.distance + CAR_LENGTH / 2;
            double rear = vehicle.distance - CAR_LENGTH / 2;
            stopLine[approach] |= detector.overlapsStopLine(front, rear);
            advance[approach] |= detector.overlapsAdvance(front, rear);
        }

        return new DetectorSnapshot(stopLine, advance);
    }

    public SimulationSnapshot snapshot() {
        List<SimulationSnapshot.Car> cars = new ArrayList<>();
        int[] queues = new int[4];
        int[] backlog = new int[4];
        for (Vehicle vehicle : vehicles) {
            cars.add(new SimulationSnapshot.Car(vehicle));
            if (vehicle.distance < RoadNetwork.INBOUND && vehicle.speed < .5) {
                queues[vehicle.route.origin.ordinal()]++;
            }
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
                controller.pendingEw(),
                detection,
                controller.strategy(),
                controller.reason(),
                controller.events());
    }
    // Scripted scenarios use these package-level seams without real-time scheduling or UI.
    Vehicle inject(RoadNetwork.Arm arm, RoadNetwork.Turn turn, double distance, double speed) {
        Vehicle vehicle = new Vehicle(new Arrival(nextId++, time(), network.route(arm, turn), 0));
        vehicle.distance = distance;
        vehicle.previousDistance = distance;
        vehicle.speed = speed;
        vehicles.add(vehicle);
        generated++;
        return vehicle;
    }

    void blockExit(RoadNetwork.Arm arm, boolean blocked) {
        blockedExits[arm.ordinal()] = blocked;
    }
}
