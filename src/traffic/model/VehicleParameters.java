package traffic.model;

/** Physical units and numerical step for the urban-car scenario. */
public final class VehicleParameters {
    public static final double DT = 0.02;
    public static final double SPEED_LIMIT = 50.0 / 3.6;
    public static final double CAR_LENGTH = 4.5;
    public static final double CAR_WIDTH = 1.8;
    public static final double GAP = 2.0;

    private VehicleParameters() {}
}
