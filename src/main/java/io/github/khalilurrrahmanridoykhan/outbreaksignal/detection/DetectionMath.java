package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

/**
 * The handful of statistics every strategy in this package needs. Kept tiny
 * and dependency-free rather than pulling in a statistics library for three
 * formulas that are each easy to hand-verify.
 */
final class DetectionMath {

    private DetectionMath() {
    }

    static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /** The sample standard deviation (n-1 denominator), matching the usual convention for a baseline estimated from data. */
    static double sampleStandardDeviation(double[] values, double mean) {
        if (values.length < 2) {
            throw new IllegalArgumentException("Need at least 2 values for a sample standard deviation, got " + values.length);
        }
        double sumSquaredDeviations = 0;
        for (double value : values) {
            double deviation = value - mean;
            sumSquaredDeviations += deviation * deviation;
        }
        return Math.sqrt(sumSquaredDeviations / (values.length - 1));
    }

    /**
     * How many standard deviations {@code current} sits above the mean of
     * {@code baseline}. A baseline with zero variance (every value identical)
     * returns {@code 0}, deliberately never alarming no matter what
     * {@code current} is, rather than dividing by zero — the only well-defined
     * way for a perfectly flat baseline to behave.
     */
    static double zScore(double current, double[] baseline) {
        double mean = mean(baseline);
        double sd = sampleStandardDeviation(baseline, mean);
        if (sd == 0) {
            return 0;
        }
        return (current - mean) / sd;
    }
}
