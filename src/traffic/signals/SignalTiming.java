package traffic.signals;

/** Immutable scenario personality. These values are assumptions, not universal NZ settings. */
public final class SignalTiming {
    public final double minimumGreen;
    public final double passageGap;
    public final double yellow;
    public final double allRed;
    public final int northSouthMaximum;
    public final int eastWestMaximum;

    public SignalTiming(
            double minimumGreen,
            double passageGap,
            double yellow,
            double allRed,
            int northSouthMaximum,
            int eastWestMaximum) {
        if (!Double.isFinite(minimumGreen)
                || !Double.isFinite(passageGap)
                || !Double.isFinite(yellow)
                || !Double.isFinite(allRed)
                || minimumGreen < 5
                || passageGap <= 0
                || yellow < 3
                || allRed < 1
                || northSouthMaximum < minimumGreen
                || eastWestMaximum < minimumGreen
                || northSouthMaximum > 60
                || eastWestMaximum > 60) {
            throw new IllegalArgumentException("Invalid urban signal timing personality");
        }
        this.minimumGreen = minimumGreen;
        this.passageGap = passageGap;
        this.yellow = yellow;
        this.allRed = allRed;
        this.northSouthMaximum = northSouthMaximum;
        this.eastWestMaximum = eastWestMaximum;
    }

    public static SignalTiming urbanDefault() {
        return new SignalTiming(5, 3, 3, 2, 20, 20);
    }

    public SignalTiming withMaximums(int northSouth, int eastWest) {
        return new SignalTiming(minimumGreen, passageGap, yellow, allRed, northSouth, eastWest);
    }

    public int maximum(boolean northSouth) {
        return northSouth ? northSouthMaximum : eastWestMaximum;
    }
}
