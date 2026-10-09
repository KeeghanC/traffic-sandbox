package traffic.engine;

import traffic.model.RoadNetwork;
import traffic.signals.DetectorSnapshot;
import traffic.signals.IntersectionController;
import traffic.signals.SignalEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deeply immutable view published atomically to Swing. */
public final class SimulationSnapshot {
    public static final class Car {
        public final long id;
        public final RoadNetwork.MovementRoute route;
        public final double distance;
        public final double previousDistance;
        public final double speed;
        public final double waiting;
        public final int color;
        public final boolean braking;
        public final boolean reserved;

        Car(Vehicle vehicle) {
            id = vehicle.id;
            route = vehicle.route;
            distance = vehicle.distance;
            previousDistance = vehicle.previousDistance;
            speed = vehicle.speed;
            waiting = vehicle.waiting;
            color = vehicle.color;
            braking = vehicle.braking;
            reserved = vehicle.admitted;
        }
    }

    public final DetectorSnapshot detectors;
    public final IntersectionController.Strategy strategy;
    public final String controllerReason;
    public final List<SignalEvent> signalEvents;
    public final double time;
    public final double throughput;
    public final double averageDelay;
    public final double phaseRemaining;
    public final long seed;
    public final long completed;
    public final long generated;
    public final int nsGreen;
    public final int ewGreen;
    public final List<Car> cars;
    private final int[] queues;
    private final int[] backlog;
    private final int[] rates;
    private final String[] signals;
    public final IntersectionController.Mode mode;
    public final IntersectionController.Mode requestedMode;
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
            int ewGreen,
            DetectorSnapshot detectors,
            IntersectionController.Strategy strategy,
            String controllerReason,
            List<SignalEvent> signalEvents) {
        this.detectors = detectors;
        this.strategy = strategy;
        this.controllerReason = controllerReason;
        this.signalEvents = Collections.unmodifiableList(new ArrayList<>(signalEvents));
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
