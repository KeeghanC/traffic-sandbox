package traffic.engine;

import traffic.model.RoadNetwork;
import traffic.model.RoadNetwork.Arm;
import traffic.model.RoadNetwork.Turn;
import traffic.runtime.SimulationRunner;
import traffic.signals.IntersectionController;

/** Dependency-free behavioural tests, including physical geometry and long runs. */
public final class SimulationTest {
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static Simulation quiet(long seed) {
        Simulation s = new Simulation(seed);
        for (Arm arm : Arm.values()) s.setRate(arm, 0);
        return s;
    }

    private static void advance(Simulation s, double seconds) {
        for (int i = 0; i < (int) Math.round(seconds / Simulation.DT); i++) s.step();
    }

    public static void main(String[] args) {
        routes();
        deterministic();
        playback();
        following();
        blockedExit();
        signals();
        priority();
        metrics();
        reset();
        snapshot();
        detectors();
        recovery();
        stress();
        System.out.println("PASS: " + assertions + " simulation assertions");
    }

    private static void routes() {
        for (Arm arm : Arm.values())
            for (Turn turn : Turn.values()) {
                Simulation s = quiet(42);
                Vehicle v = s.inject(arm, turn, RoadNetwork.INBOUND - 10, 0);
                boolean sawExit = false;
                for (int i = 0; i < 10000 && s.metrics.completed == 0; i++) {
                    s.step();
                    if (v.distance > v.route.exitDistance + 8) {
                        RoadNetwork.Pose p = v.route.pose(v.distance);
                        check(
                                p.x * v.route.destination.x + p.y * v.route.destination.y
                                        > RoadNetwork.HALF_BOX,
                                "Correct destination for " + arm + " " + turn);
                        sawExit = true;
                    }
                }
                check(
                        s.metrics.completed == 1 && s.vehicles.isEmpty() && sawExit,
                        "Trip completed " + arm + " " + turn);
            }
        RoadNetwork n = new RoadNetwork();
        check(
                !n.conflicts(n.route(Arm.NORTH, Turn.STRAIGHT), n.route(Arm.SOUTH, Turn.STRAIGHT)),
                "Opposing straight routes compatible");
        check(
                n.conflicts(n.route(Arm.NORTH, Turn.STRAIGHT), n.route(Arm.EAST, Turn.STRAIGHT)),
                "Crossing routes conflict");
        for (RoadNetwork.MovementRoute a : n.routes)
            for (RoadNetwork.MovementRoute b : n.routes)
                check(n.conflicts(a, b) == n.conflicts(b, a), "Conflict symmetry");
    }

    private static String fingerprint(SimulationSnapshot s) {
        StringBuilder b = new StringBuilder();
        b.append(s.time)
                .append('/')
                .append(s.completed)
                .append('/')
                .append(s.phase)
                .append('/')
                .append(s.averageDelay)
                .append('/')
                .append(s.generated);
        for (int i = 0; i < 4; i++)
            b.append('/').append(s.queue(i)).append('/').append(s.backlog(i));
        for (SimulationSnapshot.Car c : s.cars)
            b.append('/')
                    .append(c.id)
                    .append(':')
                    .append(c.route.id)
                    .append(':')
                    .append(c.distance)
                    .append(':')
                    .append(c.speed)
                    .append(':')
                    .append(c.waiting);
        return b.toString();
    }

    private static void deterministic() {
        Simulation a = new Simulation(910), b = new Simulation(910);
        for (int i = 0; i < 15000; i++) {
            if (i == 2500) {
                a.preset("Heavy traffic");
                b.preset("Heavy traffic");
            }
            if (i == 7000) {
                a.controller.requestMode(IntersectionController.Mode.GIVE_WAY);
                b.controller.requestMode(IntersectionController.Mode.GIVE_WAY);
            }
            a.step();
            b.step();
            if (i % 1000 == 0)
                check(
                        fingerprint(a.snapshot()).equals(fingerprint(b.snapshot())),
                        "Seeded repeat at " + i);
        }
        Simulation c = new Simulation(911);
        advance(c, 300);
        check(
                !fingerprint(a.snapshot()).equals(fingerprint(c.snapshot())),
                "Different seeds change traffic");
    }

    private static void playback() {
        String reference = null;
        for (int speed : new int[] {1, 2, 5}) {
            SimulationRunner r = new SimulationRunner(42);
            r.setSpeed(speed);
            for (int i = 0; i < 10000 / speed; i++) r.advanceSteps(speed);
            String state = fingerprint(r.snapshot());
            if (reference == null) reference = state;
            else
                check(
                        reference.equals(state),
                        "Playback grouping leaves same model state at " + speed + "x");
            r.setPaused(true);
            r.submit(s -> s.setRate(Arm.NORTH, 900));
            r.advanceSteps(0);
            check(r.snapshot().rate(0) == 900, "Commands apply while paused");
            r.close();
        }
    }

    private static void following() {
        Simulation s = quiet(1);
        Vehicle front = s.inject(Arm.NORTH, Turn.STRAIGHT, RoadNetwork.INBOUND - 2, 0);
        Vehicle back = s.inject(Arm.NORTH, Turn.STRAIGHT, RoadNetwork.INBOUND - 12, 13);
        s.controller.requestMode(IntersectionController.Mode.GIVE_WAY);
        s.blockExit(Arm.SOUTH, true);
        for (int i = 0; i < 5000; i++) {
            s.step();
            check(
                    front.distance - back.distance >= Simulation.CAR_LENGTH + Simulation.GAP - 1e-8,
                    "Following safety gap");
        }
        check(back.speed < .5, "Follower stops behind blocked leader");
        check(s.snapshot().queue(0) == 2, "Stopped incoming vehicles count in queue");
        Simulation full = quiet(5);
        full.inject(Arm.NORTH, Turn.STRAIGHT, 0, 0);
        full.demand.queues[0].add(new Arrival(999, 0, full.network.route(Arm.NORTH, Turn.LEFT), 0));
        full.step();
        check(full.demand.queues[0].size() == 1, "Full entry retains upstream arrival");
    }

    private static void blockedExit() {
        Simulation s = quiet(2);
        Vehicle car = s.inject(Arm.NORTH, Turn.STRAIGHT, RoadNetwork.INBOUND - 2, 0);
        s.blockExit(Arm.SOUTH, true);
        advance(s, 30);
        check(
                !car.admitted && car.distance < RoadNetwork.INBOUND,
                "Blocked receiving lane prevents admission");
        s.blockExit(Arm.SOUTH, false);
        advance(s, 90);
        check(s.metrics.completed == 1, "Unblocked trip recovers");
        Simulation t = quiet(3);
        Vehicle lead =
                t.inject(
                        Arm.SOUTH,
                        Turn.LEFT,
                        t.network.route(Arm.SOUTH, Turn.LEFT).exitDistance + 1,
                        0);
        Vehicle merge = t.inject(Arm.NORTH, Turn.RIGHT, RoadNetwork.INBOUND - 2, 0);
        t.step();
        check(!merge.admitted, "Merging receiving lane has clearance");
        check(lead.distance >= 0, "Lead retained");
    }

    private static void signals() {
        IntersectionController c = new IntersectionController();
        c.setStrategy(IntersectionController.Strategy.FIXED_TIME);
        c.setTimings(5, 7);
        c.reset();
        check(c.phase() == IntersectionController.Phase.NS_GREEN, "Starts NS green");
        for (int i = 0; i < 250; i++) c.step(.02, false, traffic.signals.DetectorSnapshot.empty());
        check(c.phase() == IntersectionController.Phase.NS_AMBER, "Green to amber");
        check(!c.allows(Arm.NORTH), "Amber blocks new reservations");
        for (int i = 0; i < 150; i++) c.step(.02, false, traffic.signals.DetectorSnapshot.empty());
        check(c.phase() == IntersectionController.Phase.ALL_RED_TO_EW, "Three seconds amber");
        for (int i = 0; i < 100; i++) c.step(.02, true, traffic.signals.DetectorSnapshot.empty());
        check(
                c.phase() == IntersectionController.Phase.ALL_RED_TO_EW,
                "All red extends when occupied");
        c.step(.02, false, traffic.signals.DetectorSnapshot.empty());
        check(c.phase() == IntersectionController.Phase.EW_GREEN, "Cleared junction releases EW");
        for (int i = 0; i < 350; i++) c.step(.02, false, traffic.signals.DetectorSnapshot.empty());
        check(c.phase() == IntersectionController.Phase.EW_AMBER, "EW green duration");
        for (int i = 0; i < 250; i++) c.step(.02, false, traffic.signals.DetectorSnapshot.empty());
        check(c.phase() == IntersectionController.Phase.NS_GREEN, "Full phase cycle");
        c.setTimings(60, 60);
        for (int i = 0; i < 250; i++) c.step(.02, false, traffic.signals.DetectorSnapshot.empty());
        check(
                c.phase() == IntersectionController.Phase.NS_AMBER,
                "Changed timing waits for boundary");
        c.requestMode(IntersectionController.Mode.GIVE_WAY);
        c.step(.02, true, traffic.signals.DetectorSnapshot.empty());
        check(
                c.mode() == IntersectionController.Mode.LIGHTS && !c.allows(Arm.NORTH),
                "Mode switch closes admissions");
        for (int i = 0; i < 250; i++) c.step(.02, true, traffic.signals.DetectorSnapshot.empty());
        check(c.mode() == IntersectionController.Mode.LIGHTS, "Mode waits for occupied junction");
        c.step(.02, false, traffic.signals.DetectorSnapshot.empty());
        check(
                c.mode() == IntersectionController.Mode.GIVE_WAY,
                "Mode switches after intergreen clearance");
        c.requestMode(IntersectionController.Mode.LIGHTS);
        c.step(.02, false, traffic.signals.DetectorSnapshot.empty());
        check(!c.allows(Arm.NORTH), "Switch back starts all red");
        for (int i = 0; i < 100; i++) c.step(.02, false, traffic.signals.DetectorSnapshot.empty());
        check(c.allows(Arm.NORTH), "Switch-back clearance completes");
    }

    private static void priority() {
        Simulation s = quiet(4);
        s.controller.requestMode(IntersectionController.Mode.GIVE_WAY);
        s.controller.reset();
        Vehicle minor = s.inject(Arm.EAST, Turn.STRAIGHT, RoadNetwork.INBOUND - 2, 0);
        Vehicle major = s.inject(Arm.NORTH, Turn.STRAIGHT, RoadNetwork.INBOUND - 2, 0);
        s.step();
        check(major.admitted && !minor.admitted, "Major road wins conflicting arrival");
        Simulation t = quiet(5);
        Vehicle right = t.inject(Arm.NORTH, Turn.RIGHT, RoadNetwork.INBOUND - 2, 0);
        Vehicle straight = t.inject(Arm.SOUTH, Turn.STRAIGHT, RoadNetwork.INBOUND - 2, 0);
        t.step();
        check(straight.admitted && !right.admitted, "Right turn yields to opposing straight");
        Simulation tie = quiet(6);
        tie.controller.requestMode(IntersectionController.Mode.GIVE_WAY);
        tie.controller.reset();
        Vehicle first = tie.inject(Arm.NORTH, Turn.RIGHT, RoadNetwork.INBOUND - 2, 0);
        Vehicle second = tie.inject(Arm.SOUTH, Turn.RIGHT, RoadNetwork.INBOUND - 2, 0);
        tie.step();
        if (tie.network.conflicts(first.route, second.route))
            check(first.admitted && !second.admitted, "Vehicle ID resolves equal arrivals");
        else check(first.admitted && second.admitted, "Compatible opposing turns can proceed");
    }

    private static void metrics() {
        Simulation s = quiet(7);
        check(
                Double.isNaN(s.snapshot().averageDelay) && Double.isNaN(s.snapshot().throughput),
                "No data represented as unavailable");
        s.metrics.complete(10, 4);
        s.metrics.complete(20, 8);
        check(s.snapshot().averageDelay == 6, "Mean delay calculation");
        s.metrics.expire(75);
        check(s.metrics.recent.size() == 1, "Rolling minute expires old completion");
        Simulation queued = quiet(9);
        queued.blockExit(Arm.SOUTH, true);
        queued.inject(Arm.NORTH, Turn.STRAIGHT, 0, 0);
        queued.demand.queues[0].add(
                new Arrival(999, 0, queued.network.route(Arm.NORTH, Turn.STRAIGHT), 0));
        advance(queued, 60);
        check(queued.metrics.completed == 0, "Blocked trips not completed");
        queued.blockExit(Arm.SOUTH, false);
        advance(queued, 120);
        check(
                queued.metrics.completed == 2 && queued.snapshot().averageDelay > 30,
                "Delay includes upstream and junction wait");
    }

    private static void reset() {
        Simulation s = new Simulation(12);
        s.preset("Heavy traffic");
        advance(s, 100);
        s.blockExit(Arm.NORTH, true);
        s.controller.requestMode(IntersectionController.Mode.GIVE_WAY);
        s.reset(12);
        check(
                s.time() == 0 && s.vehicles.isEmpty() && s.metrics.completed == 0 && !s.occupied(),
                "Reset clears model state");
        for (int i = 0; i < 4; i++)
            check(
                    s.demand.queues[i].isEmpty() && !s.blockedExits[i],
                    "Reset clears queue/obstruction");
        check(
                s.controller.mode() == IntersectionController.Mode.GIVE_WAY,
                "Reset starts requested control mode");
        SimulationRunner runner = new SimulationRunner(11);
        runner.advanceSteps(1);
        runner.submit(x -> x.preset("Heavy traffic"));
        runner.advanceSteps(3000);
        runner.repeat();
        runner.advanceSteps(0);
        check(
                runner.snapshot().time == 0 && runner.snapshot().rate(0) == 300,
                "Repeat restores starting profile");
        runner.advanceSteps(3000);
        Simulation original = new Simulation(11);
        advance(original, 60);
        check(
                fingerprint(runner.snapshot()).equals(fingerprint(original.snapshot())),
                "Repeat reproduces initial seed/profile");
    }

    private static void snapshot() {
        Simulation s = new Simulation(15);
        SimulationSnapshot before = s.snapshot();
        s.setRate(Arm.NORTH, 1200);
        s.inject(Arm.NORTH, Turn.LEFT, 0, 0);
        check(before.rate(0) == 300 && before.cars.isEmpty(), "Published snapshot immutable");
        try {
            before.cars.add(null);
            throw new AssertionError("List mutable");
        } catch (UnsupportedOperationException expected) {
            assertions++;
        }
        try {
            s.setRate(Arm.NORTH, 1201);
            throw new AssertionError("Invalid rate accepted");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static void recovery() {
        Simulation s = new Simulation(88);
        s.preset("Heavy traffic");
        s.blockExit(Arm.SOUTH, true);
        advance(s, 90);
        long done = s.metrics.completed;
        s.blockExit(Arm.SOUTH, false);
        advance(s, 180);
        check(
                s.metrics.completed > done + 30,
                "Traffic resumes completing trips after obstruction: before="
                        + done
                        + " after="
                        + s.metrics.completed);
        assertSafe(s);
        for (Arm arm : Arm.values()) s.setRate(arm, 0);
        advance(s, 1500);
        check(
                s.vehicles.isEmpty(),
                "Queued traffic drains after demand stops and obstruction clears");
        for (int i = 0; i < 4; i++) check(s.demand.queues[i].isEmpty(), "Upstream backlog drains");
    }

    private static void detectors() {
        Simulation simulation = quiet(45);
        advance(simulation, 120);
        check(
                simulation.controller.phase() == IntersectionController.Phase.NS_GREEN,
                "Empty intersection rests on green");
        simulation.inject(Arm.EAST, Turn.STRAIGHT, RoadNetwork.INBOUND - 100, 0);
        simulation.step();
        check(
                !simulation.snapshot().detectors.demand(false),
                "Distant arrival cannot call a signal without a detector");
        Vehicle detected = simulation.inject(Arm.WEST, Turn.STRAIGHT, RoadNetwork.INBOUND - 46, 0);
        simulation.step();
        check(
                simulation.snapshot().detectors.advance(Arm.WEST),
                "Physical body occupies advance loop");
        check(
                simulation.controller.phase() == IntersectionController.Phase.NS_AMBER,
                "Measured approach demand starts safe transition");
        advance(simulation, 160);
        check(simulation.metrics.completed == 2, "Detected cross-road demand is served and clears");
        check(detected.cleared, "Approach detector and controller integrate with routing");
    }

    private static void stress() {
        long start = System.nanoTime();
        Simulation s = new Simulation(20261010);
        s.preset("Heavy traffic");
        int peak = 0;
        for (int i = 0; i < 90000; i++) {
            if (i == 30000) s.controller.requestMode(IntersectionController.Mode.GIVE_WAY);
            if (i == 45000) s.blockExit(Arm.WEST, true);
            if (i == 50000) s.blockExit(Arm.WEST, false);
            if (i == 60000) s.controller.requestMode(IntersectionController.Mode.LIGHTS);
            s.step();
            peak = Math.max(peak, s.vehicles.size());
            if (i % 50 == 0) assertSafe(s);
        }
        check(
                s.metrics.completed > 300,
                "Stress scenario completes hundreds of trips: completed="
                        + s.metrics.completed
                        + " generated="
                        + s.snapshot().generated);
        check(peak <= Simulation.MAX_ACTIVE, "Active vehicles bounded");
        System.out.printf(
                "30-minute stress: %,d completed, peak %d cars, %.2fs wall time%n",
                s.metrics.completed, peak, (System.nanoTime() - start) / 1e9);
    }

    static void assertSafe(Simulation s) {
        for (Vehicle v : s.vehicles) {
            check(
                    Double.isFinite(v.distance) && Double.isFinite(v.speed) && v.speed >= 0,
                    "Finite, nonnegative state");
        }
        for (int i = 0; i < s.vehicles.size(); i++)
            for (int j = i + 1; j < s.vehicles.size(); j++) {
                Vehicle a = s.vehicles.get(i), b = s.vehicles.get(j);
                check(
                        !(a.admitted && b.admitted && s.network.conflicts(a.route, b.route)),
                        "Conflicting movement reservations");
                RoadNetwork.Pose pa = a.route.pose(a.distance), pb = b.route.pose(b.distance);
                if (Math.abs(pa.x - pb.x) < 7 && Math.abs(pa.y - pb.y) < 7)
                    check(
                            !overlap(pa, pb),
                            "Vehicle collision at t="
                                    + s.time()
                                    + " cars "
                                    + a.id
                                    + "/"
                                    + b.id
                                    + " routes "
                                    + a.route.id
                                    + "/"
                                    + b.route.id);
            }
    }

    private static boolean overlap(RoadNetwork.Pose a, RoadNetwork.Pose b) {
        double[][] axes = {
            {Math.cos(a.angle), Math.sin(a.angle)},
            {-Math.sin(a.angle), Math.cos(a.angle)},
            {Math.cos(b.angle), Math.sin(b.angle)},
            {-Math.sin(b.angle), Math.cos(b.angle)}
        };
        for (double[] axis : axes) {
            double distance = Math.abs((b.x - a.x) * axis[0] + (b.y - a.y) * axis[1]);
            double ra =
                    Simulation.CAR_LENGTH
                                    / 2
                                    * Math.abs(
                                            Math.cos(a.angle) * axis[0]
                                                    + Math.sin(a.angle) * axis[1])
                            + Simulation.CAR_WIDTH
                                    / 2
                                    * Math.abs(
                                            -Math.sin(a.angle) * axis[0]
                                                    + Math.cos(a.angle) * axis[1]);
            double rb =
                    Simulation.CAR_LENGTH
                                    / 2
                                    * Math.abs(
                                            Math.cos(b.angle) * axis[0]
                                                    + Math.sin(b.angle) * axis[1])
                            + Simulation.CAR_WIDTH
                                    / 2
                                    * Math.abs(
                                            -Math.sin(b.angle) * axis[0]
                                                    + Math.cos(b.angle) * axis[1]);
            if (distance >= ra + rb - .02) return false;
        }
        return true;
    }
}
