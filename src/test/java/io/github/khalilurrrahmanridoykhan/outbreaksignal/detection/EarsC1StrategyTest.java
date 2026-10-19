package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class EarsC1StrategyTest {

    private final EarsC1Strategy strategy =
            new EarsC1Strategy(new OutbreakProperties.Detection.Ears(7, 3.0));

    @Test
    void aFlatSeriesNeverAlerts() {
        List<WeeklyValue> series = TestSeries.of(10, 10, 10, 10, 10, 10, 10, 10);

        assertThat(strategy.evaluate(series)).allSatisfy(point -> assertThat(point.alarmed()).isFalse());
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
    void idIsStable() {
        assertThat(strategy.id()).isEqualTo("EARS_C1");
    }
}
