package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Expected values below were computed independently with Python's
 * {@code statistics.mean}/{@code statistics.stdev} (sample standard
 * deviation), not derived from this strategy's own code.
 */
class MovingAverageZStrategyTest {

    private final MovingAverageZStrategy strategy =
            new MovingAverageZStrategy(new OutbreakProperties.Detection.MovingAverage(7, 3.0));

    @Test
    void aFlatSeriesNeverAlerts() {
        List<WeeklyValue> series = TestSeries.of(10, 10, 10, 10, 10, 10, 10, 10, 10, 10);

        List<DetectionPoint> points = strategy.evaluate(series);

        assertThat(points).isNotEmpty();
        assertThat(points).allSatisfy(point -> {
            assertThat(point.score()).isZero();
            assertThat(point.alarmed()).isFalse();
        });
    }

    @Test
    void flagsASpikeWithTheHandComputedZScore() {
        List<WeeklyValue> series = TestSeries.of(10, 12, 11, 9, 13, 10, 11, 25);

        List<DetectionPoint> points = strategy.evaluate(series);

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().score()).isCloseTo(10.513686, within(1e-5));
        assertThat(points.getFirst().alarmed()).isTrue();
    }

    @Test
    void aValueBelowThresholdIsNotAlarmed() {
        List<WeeklyValue> series = TestSeries.of(10, 12, 11, 9, 13, 10, 11, 12);

        List<DetectionPoint> points = strategy.evaluate(series);

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().score()).isCloseTo(0.849591, within(1e-5));
        assertThat(points.getFirst().alarmed()).isFalse();
    }

    @Test
    void tooFewWeeksForABaselineProducesNoPoints() {
        List<WeeklyValue> series = TestSeries.of(10, 12, 11, 9, 13, 10, 11); // exactly baselineWeeks, no current week

        assertThat(strategy.evaluate(series)).isEmpty();
    }

    @Test
    void scalingEveryValueByAPositiveFactorLeavesTheScoreUnchanged() {
        List<WeeklyValue> series = TestSeries.of(10, 12, 11, 9, 13, 10, 11, 25);
        List<WeeklyValue> scaled = TestSeries.scaledBy(series, 100.0);

        List<DetectionPoint> original = strategy.evaluate(series);
        List<DetectionPoint> rescaled = strategy.evaluate(scaled);

        assertThat(rescaled.getFirst().score()).isCloseTo(original.getFirst().score(), within(1e-9));
    }

    @Test
    void idAndParametersAreStable() {
        assertThat(strategy.id()).isEqualTo("MOVING_AVERAGE_Z");
        assertThat(strategy.parameters()).containsEntry("baselineWeeks", 7).containsEntry("thresholdK", 3.0);
    }
}
