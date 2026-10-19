package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Expected cumulative sums below were computed independently in Python by
 * replaying the documented recurrence
 * {@code S_t = max(0, S_(t-1) + (x_t - mean0) - k*sd0)} by hand, not derived
 * from this class.
 */
class CusumStrategyTest {

    private final CusumStrategy strategy =
            new CusumStrategy(new OutbreakProperties.Detection.Cusum(8, 0.5, 5.0));

    @Test
    void aSmallSustainedShiftEventuallyCrossesTheThresholdExactlyWhenComputedByHand() {
        List<WeeklyValue> series = TestSeries.of(
                10, 11, 9, 10, 12, 9, 11, 10, // 8-week baseline, mean 10.25, sd ~1.035099
                12, 13, 15, 16, 18, 20, 21, 23, 25, 26);

        List<DetectionPoint> points = strategy.evaluate(series);

        assertThat(points).hasSize(10);
        assertThat(points.get(0).score()).isCloseTo(1.232451, within(1e-5));
        assertThat(points.get(0).alarmed()).isFalse();
        assertThat(points.get(1).score()).isCloseTo(3.464902, within(1e-5));
        assertThat(points.get(1).alarmed()).isFalse();
        assertThat(points.get(2).score()).isCloseTo(7.697352, within(1e-5));
        assertThat(points.get(2).alarmed()).isTrue();
        assertThat(points.get(9).score()).isCloseTo(81.324508, within(1e-5));
    }

    @Test
    void aFlatSeriesNeverAlertsEvenThoughTheBaselineStandardDeviationIsZero() {
        List<WeeklyValue> series = TestSeries.of(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);

        List<DetectionPoint> points = strategy.evaluate(series);

        assertThat(points).isNotEmpty();
        assertThat(points).allSatisfy(point -> {
            assertThat(point.score()).isZero();
            assertThat(point.alarmed()).isFalse();
        });
    }

    @Test
    void theCumulativeSumNeverGoesNegative() {
        // A sustained drop should floor the statistic at 0, not run away negative.
        List<WeeklyValue> series = TestSeries.of(10, 11, 9, 10, 12, 9, 11, 10, 1, 1, 1, 1, 1);

        List<DetectionPoint> points = strategy.evaluate(series);

        assertThat(points).allSatisfy(point -> assertThat(point.score()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void tooFewWeeksForTheBaselineProducesNoPoints() {
        List<WeeklyValue> series = TestSeries.of(10, 11, 9, 10, 12, 9, 11, 10); // exactly baselineWeeks

        assertThat(strategy.evaluate(series)).isEmpty();
    }

    @Test
    void idIsStable() {
        assertThat(strategy.id()).isEqualTo("CUSUM");
    }
}
