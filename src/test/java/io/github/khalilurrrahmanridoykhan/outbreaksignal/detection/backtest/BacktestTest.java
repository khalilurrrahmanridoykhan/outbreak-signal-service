package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.CusumStrategy;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.DetectionStrategy;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.EarsC1Strategy;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.EarsC2Strategy;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.EarsC3Strategy;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.MovingAverageZStrategy;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The project's "one command" backtest: {@code ./mvnw test -Dtest=BacktestTest}.
 * Prints a sensitivity / false-alarm / timeliness table to stdout for every
 * strategy, against a fixed seed, and makes loose sanity-check assertions so
 * a real regression fails the build rather than only showing up if someone
 * reads the printed table. The numbers behind {@code RESULTS.md} were copied
 * from this test's own output, unedited.
 */
class BacktestTest {

    // Matches the production defaults in OutbreakProperties.Detection;
    // update both together if those ever change.
    private static final List<DetectionStrategy> STRATEGIES = List.of(
            new MovingAverageZStrategy(new OutbreakProperties.Detection.MovingAverage(12, 3.0)),
            new EarsC1Strategy(new OutbreakProperties.Detection.Ears(7, 3.0)),
            new EarsC2Strategy(new OutbreakProperties.Detection.Ears(7, 3.0)),
            new EarsC3Strategy(new EarsC2Strategy(new OutbreakProperties.Detection.Ears(7, 3.0)),
                    new OutbreakProperties.Detection.Ears(7, 3.0)),
            new CusumStrategy(new OutbreakProperties.Detection.Cusum(8, 0.5, 5.0)));

    // A fixed, arbitrary seed — not special in any way, just always the same
    // one, so the printed table is exactly reproducible.
    private static final long SEED = 42L;
    private static final int REPLICATES = 200;
    private static final int TOTAL_WEEKS = 104;
    private static final int OUTBREAK_START_INDEX = 60;
    private static final int OUTBREAK_DURATION_WEEKS = 4;
    private static final double BASELINE_MEAN = 20.0;
    private static final double SEASONAL_AMPLITUDE = 5.0;
    private static final double OUTBREAK_MAGNITUDE = 15.0;
    private static final int GRACE_WEEKS = 4;

    @Test
    void runsTheBacktestAndPrintsEveryStrategysMeasuredPerformance() {
        List<BacktestResult> results = BacktestEvaluator.evaluate(
                STRATEGIES, REPLICATES, SEED, TOTAL_WEEKS, OUTBREAK_START_INDEX, OUTBREAK_DURATION_WEEKS,
                BASELINE_MEAN, SEASONAL_AMPLITUDE, OUTBREAK_MAGNITUDE, GRACE_WEEKS);

        printTable(results);

        assertThat(results).hasSize(5);
        for (BacktestResult result : results) {
            // Loose bounds: this is a regression guard against something
            // badly broken (a strategy that stops detecting almost
            // anything, or starts false-alarming constantly), not a
            // re-assertion of the exact measured numbers in RESULTS.md.
            assertThat(result.sensitivity())
                    .as("%s sensitivity", result.algorithm())
                    .isBetween(0.3, 1.0);
            assertThat(result.falseAlarmEpisodesPer100())
                    .as("%s false-alarm episode rate", result.algorithm())
                    .isLessThan(10.0);
            assertThat(result.medianTimelinessWeeks())
                    .as("%s timeliness", result.algorithm())
                    .isNotNull();
        }
    }

    private static void printTable(List<BacktestResult> results) {
        System.out.printf(Locale.ROOT,
                "%-16s %12s %18s %22s %16s%n",
                "algorithm", "sensitivity", "faWeeksPer100", "faEpisodesPer100wk", "medianDelayWks");
        for (BacktestResult r : results) {
            System.out.printf(Locale.ROOT, "%-16s %12.3f %18.3f %22.3f %16s%n",
                    r.algorithm(), r.sensitivity(), r.falseAlarmWeeksPer100(), r.falseAlarmEpisodesPer100(),
                    r.medianTimelinessWeeks());
        }
    }
}
