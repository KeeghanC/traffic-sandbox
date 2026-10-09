package traffic.signals;

import traffic.model.RoadNetwork.Arm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Isolated two-phase NZ-inspired controller, driven solely by detector inputs and simulation time.
 * This models local vehicle actuation; it does not reproduce SCATS network optimisation.
 */
public final class IntersectionController {
    public enum Mode {
        LIGHTS,
        GIVE_WAY
    }

    public enum Strategy {
        ACTUATED,
        FIXED_TIME
    }

    public enum Phase {
        NS_GREEN,
        NS_AMBER,
        ALL_RED_TO_EW,
        EW_GREEN,
        EW_AMBER,
        ALL_RED_TO_NS
    }

    private Mode mode = Mode.LIGHTS;
    private Mode requestedMode = Mode.LIGHTS;
    private Strategy strategy = Strategy.ACTUATED;
    private Phase phase = Phase.NS_GREEN;
    private SignalTiming timing = SignalTiming.urbanDefault();
    private SignalTiming pendingTiming = timing;
    private double elapsed;
    private double time;
    private double gapElapsed;
    private double opposingCallElapsed;
    private boolean northSouthCall;
    private boolean eastWestCall;
    private String reason = "Resting green · no opposing demand";
    private final Deque<SignalEvent> trace = new ArrayDeque<>();

    public Mode mode() {
        return mode;
    }

    public Mode requestedMode() {
        return requestedMode;
    }

    public Strategy strategy() {
        return strategy;
    }

    public Phase phase() {
        return phase;
    }

    public double elapsed() {
        return elapsed;
    }

    public int pendingNs() {
        return pendingTiming.northSouthMaximum;
    }

    public int pendingEw() {
        return pendingTiming.eastWestMaximum;
    }

    public String reason() {
        return reason;
    }

    public boolean switching() {
        return mode != requestedMode;
    }

    public boolean called(boolean northSouth) {
        return northSouth ? northSouthCall : eastWestCall;
    }

    public List<SignalEvent> events() {
        return new ArrayList<>(trace);
    }

    public void requestMode(Mode mode) {
        requestedMode = Objects.requireNonNull(mode);
        if (this.mode == Mode.GIVE_WAY && requestedMode == Mode.LIGHTS) {
            transition(Phase.ALL_RED_TO_NS, "Mode change · all-red clearance");
        }
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = Objects.requireNonNull(strategy);
    }

    public void setTimings(int northSouth, int eastWest) {
        pendingTiming = pendingTiming.withMaximums(northSouth, eastWest);
    }

    public void configure(SignalTiming timing) {
        pendingTiming = Objects.requireNonNull(timing);
    }

    public void step(double dt, boolean occupied, DetectorSnapshot detectors) {
        if (!Double.isFinite(dt) || dt <= 0) {
            throw new IllegalArgumentException("Positive finite step required");
        }
        Objects.requireNonNull(detectors);
        time += dt;
        elapsed += dt;
        rememberCalls(detectors);

        if (switching()) {
            advanceModeChange(occupied);
        } else if (mode == Mode.LIGHTS) {
            advanceSignals(dt, occupied, detectors);
        } else {
            reason = "Give way · north–south priority";
        }
    }

    private void rememberCalls(DetectorSnapshot detectors) {
        northSouthCall |= detectors.demand(true);
        eastWestCall |= detectors.demand(false);
    }

    private void advanceSignals(double dt, boolean occupied, DetectorSnapshot detectors) {
        if (isGreen()) {
            advanceGreen(dt, detectors);
            return;
        }

        if (isYellow()) {
            if (elapsed + 1e-9 >= timing.yellow) {
                transition(
                        phase == Phase.NS_AMBER ? Phase.ALL_RED_TO_EW : Phase.ALL_RED_TO_NS,
                        "All-red clearance");
            }
            return;
        }

        if (elapsed + 1e-9 < timing.allRed) {

            return;
        }
        if (occupied) {
            reason = "Clearance extended · junction occupied";
            return;
        }
        beginGreen(phase == Phase.ALL_RED_TO_NS);
    }

    private void advanceGreen(double dt, DetectorSnapshot detectors) {
        boolean northSouth = phase == Phase.NS_GREEN;
        boolean opposingDemand = called(!northSouth);
        clearCall(northSouth);
        gapElapsed = detectors.demand(northSouth) ? 0 : gapElapsed + dt;
        opposingCallElapsed = opposingDemand ? opposingCallElapsed + dt : 0;

        if (strategy == Strategy.FIXED_TIME) {
            reason = "Fixed-time comparison";
            if (elapsed + 1e-9 >= timing.maximum(northSouth)) {
                endGreen("Fixed green elapsed");
            }
            return;
        }

        if (elapsed + 1e-9 < timing.minimumGreen) {
            reason = "Minimum green";
        } else if (!opposingDemand) {
            reason = "Resting green · no opposing demand";
        } else if (opposingCallElapsed + 1e-9 >= timing.maximum(northSouth)) {
            endGreen("Maximum green · opposing demand");
        } else if (gapElapsed + 1e-9 >= timing.passageGap) {
            endGreen("Gap detected · opposing demand");
        } else {
            reason = "Green extended · vehicles detected";
        }
    }

    private void advanceModeChange(boolean occupied) {
        // A requested mode change must not abruptly turn a green display red.
        if (mode == Mode.LIGHTS && isGreen()) {
            endGreen("Mode change requested");
            return;
        }
        if (mode == Mode.GIVE_WAY && !isAllRed()) {
            transition(Phase.ALL_RED_TO_NS, "Mode change · all-red clearance");
            return;
        }
        if (isYellow()) {
            if (elapsed + 1e-9 >= timing.yellow) {
                transition(Phase.ALL_RED_TO_NS, "Mode change · all-red clearance");
            }
            return;
        }
        if (elapsed + 1e-9 < timing.allRed || occupied) {
            reason =
                    occupied
                            ? "Mode change · waiting for junction clearance"
                            : "Mode change · all-red clearance";
            return;
        }

        mode = requestedMode;
        if (mode == Mode.LIGHTS) {
            beginGreen(true);
        } else {
            reason = "Give way · north–south priority";
            recordEvent();
        }
    }

    private void endGreen(String reason) {
        transition(phase == Phase.NS_GREEN ? Phase.NS_AMBER : Phase.EW_AMBER, reason);
    }

    private void beginGreen(boolean northSouth) {
        timing = pendingTiming;
        clearCall(northSouth);
        gapElapsed = 0;
        opposingCallElapsed = 0;
        transition(northSouth ? Phase.NS_GREEN : Phase.EW_GREEN, "Demand served · minimum green");
    }

    private void clearCall(boolean northSouth) {
        if (northSouth) {
            northSouthCall = false;
        } else eastWestCall = false;
    }

    private void transition(Phase next, String reason) {
        phase = next;
        elapsed = 0;
        this.reason = reason;
        recordEvent();
    }

    private void recordEvent() {
        trace.addLast(new SignalEvent(time, phase, reason));
        while (trace.size() > 64) {
            trace.removeFirst();
        }
    }

    private boolean isGreen() {
        return phase == Phase.NS_GREEN || phase == Phase.EW_GREEN;
    }

    private boolean isYellow() {
        return phase == Phase.NS_AMBER || phase == Phase.EW_AMBER;
    }

    private boolean isAllRed() {
        return phase == Phase.ALL_RED_TO_NS || phase == Phase.ALL_RED_TO_EW;
    }

    public boolean allows(Arm arm) {
        if (switching()) {
            return false;
        }
        if (mode == Mode.GIVE_WAY) {
            return true;
        }
        return phase == Phase.NS_GREEN && arm.northSouth()
                || phase == Phase.EW_GREEN && !arm.northSouth();
    }

    public String signal(Arm arm) {
        if (mode == Mode.GIVE_WAY && !switching()) {
            return arm.northSouth() ? "PRIORITY" : "YIELD";
        }
        if (mode == Mode.LIGHTS && isYellow() && arm.northSouth() == (phase == Phase.NS_AMBER))
            return "AMBER";
        if (allows(arm)) {
            return "GREEN";
        }
        return "RED";
    }

    /** Safety intervals have a countdown; actuated green has no predetermined end time. */
    public double remaining() {
        if (isYellow()) {
            return Math.max(0, timing.yellow - elapsed);
        }
        if (isAllRed()) {
            return Math.max(0, timing.allRed - elapsed);
        }
        if (strategy == Strategy.ACTUATED) {
            return Double.NaN;
        }
        return Math.max(0, timing.maximum(phase == Phase.NS_GREEN) - elapsed);
    }

    public void reset() {
        mode = requestedMode;
        phase = Phase.NS_GREEN;
        timing = pendingTiming;
        time = 0;
        elapsed = 0;
        gapElapsed = 0;
        opposingCallElapsed = 0;
        northSouthCall = false;
        eastWestCall = false;
        trace.clear();
        reason = "Resting green · no opposing demand";
    }
}
