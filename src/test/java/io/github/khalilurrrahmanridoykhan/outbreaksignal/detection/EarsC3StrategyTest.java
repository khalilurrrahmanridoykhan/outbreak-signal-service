package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Expected C3 sums below were computed independently in Python by replaying
 * this class's own documented formula (not by running the Java code): each
 * week's C2 z-score first, then a 3-week rolling sum of {@code max(0, z-1)}.
 */
class EarsC3StrategyTest {

    private final OutbreakProperties.Detection.Ears config = new OutbreakProperties.Detection.Ears(7, 3.0);
    private final EarsC3Strategy strategy = new EarsC3Strategy(new EarsC2Strategy(config), config);

    @Test
    void aSustainedModerateRiseAccumulatesPastTheThresholdEvenIfNoSingleWeekWould() {
        // 7-week flat-ish baseline, 2 guard weeks (moderately elevated, as a
        // real ramping outbreak's immediately-preceding weeks would be), then
        // a sustained rise. Individually each week's C2 z-score is well under
        // the 3.0 single-week threshold at first, but the 3-week cumulative
        // sum crosses it by the first point C3 can evaluate.
        List<WeeklyValue> series = TestSeries.of(
                10, 11, 9, 10, 11, 9, 10, // baseline
                12, 12, // guard
                14, 15, 16, 17, 18, 19, 20); // sustained rise

        List<DetectionPoint> points = strategy.evaluate(series);

        assertThat(points).hasSize(5);
        assertThat(points.get(0).score()).isCloseTo(10.514403, within(1e-5));
        assertThat(points.get(1).score()).isCloseTo(9.109799, within(1e-5));
        assertThat(points.get(4).score()).isCloseTo(4.918861, within(1e-5));
        assertThat(points).allSatisfy(point -> assertThat(point.alarmed()).isTrue());
    }

    @Test
    void aFlatSeriesNeverAlerts() {
        List<WeeklyValue> series = TestSeries.of(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);

        assertThat(strategy.evaluate(series)).allSatisfy(point -> assertThat(point.alarmed()).isFalse());
    }

    @Test
    void idIsStable() {
        assertThat(strategy.id()).isEqualTo("EARS_C3");
    }
}
