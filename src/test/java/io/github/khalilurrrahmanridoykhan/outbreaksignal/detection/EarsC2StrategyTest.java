package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class EarsC2StrategyTest {

    private final EarsC2Strategy strategy =
            new EarsC2Strategy(new OutbreakProperties.Detection.Ears(7, 3.0));

    @Test
    void theTwoGuardWeeksImmediatelyBeforeTheCurrentOneNeverEnterTheBaseline() {
        // Baseline is the same 7 weeks as the EarsC1 fixture; the 2 weeks right
        // before the current one are wildly elevated (50, 60) — if the guard
        // band were missing or wrong, they would dominate the baseline's
        // standard deviation and the z-score below would come out very
        // different from EarsC1's hand-computed 10.513686 on the same baseline.
        List<WeeklyValue> series = TestSeries.of(10, 12, 11, 9, 13, 10, 11, /* guard */ 50, 60, /* current */ 25);

        List<DetectionPoint> points = strategy.evaluate(series);

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().score()).isCloseTo(10.513686, within(1e-5));
        assertThat(points.getFirst().alarmed()).isTrue();
    }

    @Test
    void tooFewWeeksForBaselinePlusGuardProducesNoPoints() {
        // 8 weeks: exactly baselineWeeks(7) + guard(2) - 1, one short of the
        // first evaluable index.
        List<WeeklyValue> series = TestSeries.of(10, 12, 11, 9, 13, 10, 11, 50);

        assertThat(strategy.evaluate(series)).isEmpty();
    }

    @Test
    void idAndParametersIncludeTheFixedGuardBand() {
        assertThat(strategy.id()).isEqualTo("EARS_C2");
        assertThat(strategy.parameters()).containsEntry("guardWeeks", EarsC2Strategy.GUARD_WEEKS);
    }
}
