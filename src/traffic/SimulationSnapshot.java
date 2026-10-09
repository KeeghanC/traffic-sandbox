package traffic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deeply immutable view published atomically to Swing. */
public final class SimulationSnapshot {
  public static final class Car {
    public final long id;
    public final RoadNetwork.MovementRoute route;
    public final double distance, previousDistance, speed, waiting;
    public final int color;
    public final boolean braking, reserved;

    Car(Simulation.Vehicle v) {
      id = v.id;
      route = v.route;
      distance = v.distance;
      previousDistance = v.previousDistance;
      speed = v.speed;
      waiting = v.waiting;
      color = v.color;
      braking = v.braking;
      reserved = v.admitted;
    }
  }

  public final double time, throughput, averageDelay, phaseRemaining;
  public final long seed, completed, generated;
  public final int nsGreen, ewGreen;
  public final List<Car> cars;
  private final int[] queues, backlog, rates;
  private final String[] signals;
  public final IntersectionController.Mode mode, requestedMode;
  public final IntersectionController.Phase phase;

  SimulationSnapshot(
      double time,
      long seed,
      List<Car> cars,
      int[] queues,
      int[] backlog,
      int[] rates,
      String[] signals,
      long completed,
      double throughput,
      double averageDelay,
      IntersectionController.Mode mode,
      IntersectionController.Mode requestedMode,
      IntersectionController.Phase phase,
      double phaseRemaining,
      long generated,
      int nsGreen,
      int ewGreen) {
    this.time = time;
    this.seed = seed;
    this.cars = Collections.unmodifiableList(new ArrayList<>(cars));
    this.queues = queues.clone();
    this.backlog = backlog.clone();
    this.rates = rates.clone();
    this.signals = signals.clone();
    this.completed = completed;
    this.throughput = throughput;
    this.averageDelay = averageDelay;
    this.mode = mode;
    this.requestedMode = requestedMode;
    this.phase = phase;
    this.phaseRemaining = phaseRemaining;
    this.generated = generated;
    this.nsGreen = nsGreen;
    this.ewGreen = ewGreen;
  }

  public int queue(int arm) {
    return queues[arm];
  }

  public int backlog(int arm) {
    return backlog[arm];
  }

  public int rate(int arm) {
    return rates[arm];
  }

  public String signal(int arm) {
    return signals[arm];
  }
}
