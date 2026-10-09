package traffic.signals;

import traffic.model.RoadNetwork.Arm;

/** Immutable loop occupancy. Demand is measured locally, never inferred from upstream queues. */
public final class DetectorSnapshot {
    private final boolean[] stopLine;
    private final boolean[] advance;

    public DetectorSnapshot(boolean[] stopLine, boolean[] advance) {
        if (stopLine.length != 4 || advance.length != 4) {
            throw new IllegalArgumentException("Four approaches are required");
        }
        this.stopLine = stopLine.clone();
        this.advance = advance.clone();
    }

    public static DetectorSnapshot empty() {
        return new DetectorSnapshot(new boolean[4], new boolean[4]);
    }

    public boolean stopLine(Arm arm) {
        return stopLine[arm.ordinal()];
    }

    public boolean advance(Arm arm) {
        return advance[arm.ordinal()];
    }

    public boolean demand(boolean northSouth) {
        for (Arm arm : Arm.values()) {
            if (arm.northSouth() == northSouth && (stopLine(arm) || advance(arm))) {
                return true;
            }
        }
        return false;
    }
}
