package traffic.signals;

import traffic.model.RoadNetwork.Arm;
import traffic.signals.IntersectionController.Phase;

/** Scripted detector inputs verify local signal control independently of vehicle physics. */
public final class ActuatedControllerTest {
    private static int assertions;

    public static void main(String[] args) {
        restingGreen();
        minimumAndGap();
        maximumStartsWithOpposingCall();
        rememberedCall();
        safeClearance();
        switching();
        boundaryTiming();
        resetAndTrace();
        detectorGeometry();
        System.out.println("PASS: " + assertions + " isolated NZ signal controller assertions");
    }

    private static DetectorSnapshot demand(boolean northSouth, boolean eastWest) {
        return new DetectorSnapshot(
                new boolean[] {northSouth, eastWest, false, false}, new boolean[4]);
    }

    private static void advance(
            IntersectionController controller,
            double seconds,
            boolean occupied,
            DetectorSnapshot detectors) {
        for (int i = 0; i < Math.round(seconds / .02); i++)
            controller.step(.02, occupied, detectors);
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static void restingGreen() {
        IntersectionController controller = new IntersectionController();
        advance(controller, 120, false, demand(false, false));
        check(controller.phase() == Phase.NS_GREEN, "No empty-road cycling");
        check(Double.isNaN(controller.remaining()), "Actuated green has no false countdown");
        advance(controller, 120, false, demand(true, false));
        check(controller.phase() == Phase.NS_GREEN, "Main-road traffic alone keeps green");
    }

    private static void minimumAndGap() {
        IntersectionController controller = new IntersectionController();
        advance(controller, 4.98, false, demand(false, true));
        check(controller.phase() == Phase.NS_GREEN, "Minimum green is guaranteed");
        advance(controller, .02, false, demand(false, true));
        check(controller.phase() == Phase.NS_AMBER, "Minimum satisfied and gap present");
        check(controller.reason().startsWith("Gap detected"), "Gap termination explained");

        controller.reset();
        advance(controller, 8, false, demand(true, true));
        check(controller.phase() == Phase.NS_GREEN, "Detector occupancy extends green");
        advance(controller, 2.98, false, demand(false, true));
        check(controller.phase() == Phase.NS_GREEN, "Full passage gap required");
        advance(controller, .02, false, demand(false, true));
        check(controller.phase() == Phase.NS_AMBER, "Gap releases opposing traffic");
    }

    private static void maximumStartsWithOpposingCall() {
        IntersectionController controller = new IntersectionController();
        advance(controller, 100, false, demand(true, false));
        advance(controller, 19.98, false, demand(true, true));
        check(controller.phase() == Phase.NS_GREEN, "Maximum timer starts at opposing call");
        advance(controller, .02, false, demand(true, true));
        check(controller.phase() == Phase.NS_AMBER, "Continuous traffic cannot starve cross-road");
        check(controller.reason().startsWith("Maximum green"), "Maximum termination explained");
    }

    private static void rememberedCall() {
        IntersectionController controller = new IntersectionController();
        advance(controller, .02, false, demand(false, true));
        advance(controller, 9.98, false, demand(false, false));
        check(
                controller.phase() == Phase.EW_GREEN,
                "Brief detector call survives yellow and all-red");
        check(!controller.called(false), "Call clears when its phase is served");
    }

    private static void safeClearance() {
        IntersectionController controller = new IntersectionController();
        advance(controller, 5, false, demand(false, true));
        check(controller.signal(Arm.NORTH).equals("AMBER"), "Yellow displayed on departing road");
        check(!controller.allows(Arm.NORTH), "No new reservations on yellow");
        advance(controller, 2.98, true, demand(false, true));
        check(controller.phase() == Phase.NS_AMBER, "Yellow cannot end early");
        advance(controller, .02, true, demand(false, true));
        check(controller.phase() == Phase.ALL_RED_TO_EW, "Yellow then all-red");
        advance(controller, 20, true, demand(false, true));
        check(controller.phase() == Phase.ALL_RED_TO_EW, "Occupied box extends clearance");
        controller.step(.02, false, demand(false, true));
        check(controller.phase() == Phase.EW_GREEN, "Safe clearance releases next phase");
    }

    private static void switching() {
        IntersectionController controller = new IntersectionController();
        controller.requestMode(IntersectionController.Mode.GIVE_WAY);
        controller.step(.02, false, DetectorSnapshot.empty());
        check(controller.signal(Arm.NORTH).equals("AMBER"), "Mode change includes yellow");
        advance(controller, 4.98, false, DetectorSnapshot.empty());
        check(
                controller.mode() == IntersectionController.Mode.LIGHTS,
                "Full intergreen before give-way");
        advance(controller, .02, false, DetectorSnapshot.empty());
        check(
                controller.mode() == IntersectionController.Mode.GIVE_WAY,
                "Give-way after clearance");
        controller.requestMode(IntersectionController.Mode.LIGHTS);
        controller.step(.02, true, DetectorSnapshot.empty());
        advance(controller, 10, true, DetectorSnapshot.empty());
        check(
                controller.mode() == IntersectionController.Mode.GIVE_WAY,
                "Return waits for occupied box");
        controller.step(.02, false, DetectorSnapshot.empty());
        check(controller.mode() == IntersectionController.Mode.LIGHTS, "Safe return to signals");
    }

    private static void boundaryTiming() {
        IntersectionController controller = new IntersectionController();
        controller.setTimings(5, 7);
        controller.reset();
        controller.setTimings(60, 60);
        advance(controller, 5, false, demand(true, true));
        check(
                controller.phase() == Phase.NS_AMBER,
                "Pending configuration does not alter current green");
        advance(controller, 5, false, demand(true, true));
        advance(controller, 7, false, demand(true, true));
        check(controller.phase() == Phase.EW_GREEN, "New configuration applies on next green");
    }

    private static void resetAndTrace() {
        IntersectionController controller = new IntersectionController();
        advance(controller, 1000, false, demand(true, true));
        check(controller.events().size() == 64, "Decision trace is bounded");
        controller.events().clear();
        check(controller.events().size() == 64, "Trace cannot be mutated externally");
        controller.reset();
        check(!controller.called(true) && !controller.called(false), "Reset clears calls");
        check(
                controller.events().isEmpty() && controller.elapsed() == 0,
                "Reset clears timers and trace");
    }

    private static void detectorGeometry() {
        VehicleDetector detector = new VehicleDetector();
        double limit = traffic.model.RoadNetwork.INBOUND;
        check(
                detector.overlapsStopLine(limit - 1, limit - 5),
                "Stopped car occupies limit-line loop");
        check(
                !detector.overlapsStopLine(limit - 10, limit - 14),
                "Remote queue is not a detector call");
        check(detector.overlapsAdvance(limit - 45, limit - 49), "Advance loop at 45 metres");
        boolean[] state = {true, false, false, false};
        DetectorSnapshot snapshot = new DetectorSnapshot(state, new boolean[4]);
        state[0] = false;
        check(snapshot.stopLine(Arm.NORTH), "Detector snapshot defensively copies inputs");
    }
}
