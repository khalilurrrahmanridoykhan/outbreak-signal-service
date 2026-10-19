package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.backtest;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.WeeklyValue;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a synthetic weekly count series: a seasonal baseline, Poisson
 * noise around it, and — in the middle of it — a linear ramp standing in for
 * an outbreak. Every call with the same {@link Random} and parameters
 * produces the same series; see {@link BacktestEvaluator} for how many
 * independent replicates this is run for and why.
 */
final class SyntheticSeriesGenerator {

    private static final LocalDate FIRST_MONDAY = LocalDate.of(2024, 1, 1);
    private static final double SEASONAL_PERIOD_WEEKS = 52.0;
    private static final double MINIMUM_RATE = 0.5; // a Poisson mean must stay positive

    private SyntheticSeriesGenerator() {
    }

    static SyntheticSeries generate(
            Random random,
            int totalWeeks,
            int outbreakStartIndex,
            int outbreakDurationWeeks,
            double baselineMean,
            double seasonalAmplitude,
            double outbreakMagnitude) {
        List<WeeklyValue> points = new ArrayList<>(totalWeeks);
        for (int i = 0; i < totalWeeks; i++) {
            double seasonal = baselineMean + seasonalAmplitude * Math.sin(2 * Math.PI * i / SEASONAL_PERIOD_WEEKS);
            double rate = Math.max(MINIMUM_RATE, seasonal);
            if (i >= outbreakStartIndex && i < outbreakStartIndex + outbreakDurationWeeks) {
                double rampFraction = (i - outbreakStartIndex + 1) / (double) outbreakDurationWeeks;
                rate += outbreakMagnitude * rampFraction;
            }
            points.add(new WeeklyValue(FIRST_MONDAY.plusWeeks(i), poisson(random, rate)));
        }
        return new SyntheticSeries(points, outbreakStartIndex, outbreakDurationWeeks);
    }

    /** Knuth's algorithm: straightforward, exact for the rates used here, and needs no extra dependency. */
    private static int poisson(Random random, double mean) {
        double limit = Math.exp(-mean);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= random.nextDouble();
        } while (p > limit);
        return k - 1;
    }
}
