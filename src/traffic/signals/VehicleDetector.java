package traffic.signals;

import traffic.model.RoadNetwork;

/** Scenario loop geometry, measured upstream from the limit line in metres. */
public final class VehicleDetector {
    public static final double STOP_LINE_LENGTH = 6.0;
    public static final double ADVANCE_DISTANCE = 45.0;
    public static final double ADVANCE_LENGTH = 4.0;

    public boolean overlapsStopLine(double front, double rear) {
        return overlaps(front, rear, RoadNetwork.INBOUND - STOP_LINE_LENGTH, RoadNetwork.INBOUND);
    }

    public boolean overlapsAdvance(double front, double rear) {
        double end = RoadNetwork.INBOUND - ADVANCE_DISTANCE;
        return overlaps(front, rear, end - ADVANCE_LENGTH, end);
    }

    private boolean overlaps(double front, double rear, double start, double end) {
        return front >= start && rear <= end;
    }
}
