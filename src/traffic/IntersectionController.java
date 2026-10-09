package traffic;

/** Signal phase changes are simulation-time events, never wall-clock events. */
public final class IntersectionController {
  public enum Mode {
    LIGHTS,
    GIVE_WAY
  }

  public enum Phase {
    NS_GREEN,
    NS_AMBER,
    ALL_RED_TO_EW,
    EW_GREEN,
    EW_AMBER,
    ALL_RED_TO_NS
  }

  private Mode mode = Mode.LIGHTS, requestedMode = Mode.LIGHTS;
  private Phase phase = Phase.NS_GREEN;
  private double elapsed;
  private int nsGreen = 20, ewGreen = 20, pendingNs = 20, pendingEw = 20;

  public Mode mode() {
    return mode;
  }

  public Mode requestedMode() {
    return requestedMode;
  }

  public Phase phase() {
    return phase;
  }

  public double elapsed() {
    return elapsed;
  }

  public int pendingNs() {
    return pendingNs;
  }

  public int pendingEw() {
    return pendingEw;
  }

  public boolean switching() {
    return mode != requestedMode;
  }

  public void requestMode(Mode mode) {
    requestedMode = mode;
  }

  public void setTimings(int ns, int ew) {
    if (ns < 5 || ns > 60 || ew < 5 || ew > 60)
      throw new IllegalArgumentException("Green times must be 5–60 seconds");
    pendingNs = ns;
    pendingEw = ew;
  }

  public void step(double dt, boolean occupied) {
    elapsed += dt;
    if (switching()) {
      if (!occupied) {
        mode = requestedMode;
        phase = Phase.ALL_RED_TO_NS;
        elapsed = 0;
        nsGreen = pendingNs;
        ewGreen = pendingEw;
      }
      return;
    }
    if (mode == Mode.GIVE_WAY) return;
    double duration =
        phase == Phase.NS_GREEN
            ? nsGreen
            : phase == Phase.EW_GREEN
                ? ewGreen
                : phase == Phase.NS_AMBER || phase == Phase.EW_AMBER ? 3 : 1;
    if (elapsed + 1e-9 < duration) return;
    if ((phase == Phase.ALL_RED_TO_EW || phase == Phase.ALL_RED_TO_NS) && occupied) return;
    phase = Phase.values()[(phase.ordinal() + 1) % Phase.values().length];
    elapsed = 0;
    nsGreen = pendingNs;
    ewGreen = pendingEw;
  }

  public boolean allows(RoadNetwork.Arm arm) {
    if (switching()) return false;
    if (mode == Mode.GIVE_WAY) return true;
    return phase == Phase.NS_GREEN && arm.northSouth()
        || phase == Phase.EW_GREEN && !arm.northSouth();
  }

  public String signal(RoadNetwork.Arm arm) {
    if (switching()) return "RED";
    if (mode == Mode.GIVE_WAY) return arm.northSouth() ? "PRIORITY" : "YIELD";
    if (allows(arm)) return "GREEN";
    if (phase == Phase.NS_AMBER && arm.northSouth() || phase == Phase.EW_AMBER && !arm.northSouth())
      return "AMBER";
    return "RED";
  }

  public double remaining() {
    double duration =
        phase == Phase.NS_GREEN
            ? nsGreen
            : phase == Phase.EW_GREEN
                ? ewGreen
                : phase == Phase.NS_AMBER || phase == Phase.EW_AMBER ? 3 : 1;
    return Math.max(0, duration - elapsed);
  }

  public void reset() {
    mode = requestedMode;
    phase = Phase.NS_GREEN;
    elapsed = 0;
    nsGreen = pendingNs;
    ewGreen = pendingEw;
  }
}
