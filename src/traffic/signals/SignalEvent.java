package traffic.signals;

/** A bounded controller trace explains decisions in simulated time. */
public final class SignalEvent {
    public final double time;
    public final IntersectionController.Phase phase;
    public final String reason;

    SignalEvent(double time, IntersectionController.Phase phase, String reason) {
        this.time = time;
        this.phase = phase;
        this.reason = reason;
    }
}
