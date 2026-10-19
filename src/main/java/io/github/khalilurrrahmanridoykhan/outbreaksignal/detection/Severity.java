package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

/**
 * How far an alert's score sits past its strategy's threshold, as a plain
 * ratio: at least double the threshold is {@link #HIGH}, at least one and a
 * half times is {@link #MEDIUM}, otherwise {@link #LOW}. Deliberately simple
 * and the same rule for every strategy, since the strategies' thresholds are
 * already each calibrated in their own units (standard deviations); revisit
 * if a later phase needs severity to mean something more specific.
 */
public enum Severity {
    LOW,
    MEDIUM,
    HIGH;

    private static final double HIGH_RATIO = 2.0;
    private static final double MEDIUM_RATIO = 1.5;

    static Severity fromScoreOverThreshold(double score, double threshold) {
        double ratio = threshold == 0 ? Double.POSITIVE_INFINITY : score / threshold;
        if (ratio >= HIGH_RATIO) {
            return HIGH;
        }
        if (ratio >= MEDIUM_RATIO) {
            return MEDIUM;
        }
        return LOW;
    }
}
