package traffic.runtime;

import traffic.engine.Simulation;
import traffic.engine.SimulationSnapshot;
import traffic.model.RoadNetwork;
import traffic.signals.IntersectionController;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/** Single model owner; playback changes the number of identical fixed steps. */
public final class SimulationRunner implements AutoCloseable {
    private final Simulation simulation;
    private final ConcurrentLinkedQueue<Consumer<Simulation>> commands =
            new ConcurrentLinkedQueue<>();
    private volatile SimulationSnapshot snapshot;
    private volatile boolean running;
    private volatile boolean paused;
    private volatile int speed = 1;
    private volatile boolean temporaryFast;
    private volatile long publishedNanos;
    private volatile double interpolation = 1;
    private Thread thread;
    private long runSeed;
    private int[] initialRates = {300, 300, 300, 300};
    private int initialNs = 20;
    private int initialEw = 20;
    private IntersectionController.Strategy initialStrategy =
            IntersectionController.Strategy.ACTUATED;
    private IntersectionController.Mode initialMode = IntersectionController.Mode.LIGHTS;

    public SimulationRunner(long seed) {
        simulation = new Simulation(seed);
        runSeed = seed;
        publish();
    }

    public RoadNetwork network() {
        return simulation.network;
    }

    public SimulationSnapshot snapshot() {
        return snapshot;
    }

    public boolean paused() {
        return paused;
    }

    public int speed() {
        return temporaryFast ? 5 : speed;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void togglePaused() {
        paused = !paused;
    }

    public void setSpeed(int speed) {
        if (speed != 1 && speed != 2 && speed != 5)
            throw new IllegalArgumentException("Speed must be 1, 2 or 5");
        this.speed = speed;
    }

    public void temporaryFast(boolean fast) {
        temporaryFast = fast;
    }

    public void submit(Consumer<Simulation> command) {
        commands.add(command);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "traffic-simulation");
        thread.setDaemon(true);
        thread.start();
    }

    public void reset(long seed) {
        submit(
                s -> {
                    runSeed = seed;
                    s.reset(seed);
                    captureInitial(s);
                });
    }

    public void repeat() {
        submit(
                s -> {
                    for (int i = 0; i < 4; i++) {
                        s.setRate(RoadNetwork.Arm.values()[i], initialRates[i]);
                    }
                    s.controller.requestMode(initialMode);
                    s.controller.setTimings(initialNs, initialEw);
                    s.controller.setStrategy(initialStrategy);
                    s.reset(runSeed);
                });
    }

    public void configureTimings(int ns, int ew) {
        submit(
                s -> {
                    s.controller.setTimings(ns, ew);
                    if (s.time() == 0) {
                        initialNs = ns;
                        initialEw = ew;
                    }
                });
    }

    private void captureInitial(Simulation s) {
        SimulationSnapshot initial = s.snapshot();
        initialRates =
                new int[] {initial.rate(0), initial.rate(1), initial.rate(2), initial.rate(3)};
        initialStrategy = s.controller.strategy();
        initialMode = s.controller.requestedMode();
        initialNs = s.controller.pendingNs();
        initialEw = s.controller.pendingEw();
    }

    /** Advance a headless run deterministically; real-time ownership must be stopped. */
    public void advanceSteps(int steps) {
        if (running) {
            throw new IllegalStateException("Cannot manually advance a running simulation");
        }
        if (steps < 0) {
            throw new IllegalArgumentException("Steps must be nonnegative");
        }
        drain();
        if (simulation.time() == 0) {
            captureInitial(simulation);
        }
        for (int i = 0; i < steps; i++) {
            simulation.step();
        }
        publish();
    }

    private void drain() {
        Consumer<Simulation> command;
        while ((command = commands.poll()) != null) {
            command.accept(simulation);
        }
    }

    private void publish() {
        snapshot = simulation.snapshot();
        publishedNanos = System.nanoTime();
    }

    public double alpha() {
        if (paused) {
            return 1;
        }
        return Math.min(
                1,
                interpolation
                        + (System.nanoTime() - publishedNanos) / 1e9 * speed() / Simulation.DT);
    }

    private void loop() {
        long last = System.nanoTime();
        double accumulator = 0;
        try {
            while (running) {
                long now = System.nanoTime();
                double elapsed = Math.min(.1, (now - last) / 1e9);
                last = now;
                drain();
                if (simulation.time() == 0) {
                    captureInitial(simulation);
                }
                if (paused) {
                    accumulator = 0;
                    interpolation = 1;
                    publish();
                } else {
                    accumulator += elapsed * speed();
                    while (accumulator + 1e-12 >= Simulation.DT) {
                        simulation.step();
                        accumulator -= Simulation.DT;
                    }
                    interpolation = accumulator / Simulation.DT;
                    publish();
                }
                LockSupport.parkNanos(2_000_000);
            }
        } catch (Throwable error) {
            error.printStackTrace();
            running = false;
        }
    }

    @Override
    public void close() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }
}
