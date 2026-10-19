package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CDC EARS method C3: a 3-week cumulative sum of C2's excess z-score,
 * {@code sum(max(0, z - 1))} over the current week and the 2 before it. A
 * rise too slow to cross C1 or C2's own threshold on any single week can
 * still trip C3 once it has persisted for 3 weeks running.
 *
 * <p>Reuses {@link EarsC2Strategy} for the underlying z-scores rather than
 * repeating its guard-band arithmetic; C2's own alarm decision is not used
 * here, only the raw statistic.
 */
public class EarsC3Strategy implements DetectionStrategy {

    private static final int WINDOW = 3;
    private static final double EXCESS_FLOOR = 1.0;

    private final EarsC2Strategy ears2;
    private final double threshold;

    public EarsC3Strategy(EarsC2Strategy ears2, OutbreakProperties.Detection.Ears config) {
        this.ears2 = ears2;
        this.threshold = config.threshold();
    }

    @Override
    public String id() {
        return "EARS_C3";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("window", WINDOW, "excessFloor", EXCESS_FLOOR, "threshold", threshold);
    }

    @Override
    public List<DetectionPoint> evaluate(List<WeeklyValue> series) {
        List<DetectionPoint> c2Points = ears2.evaluate(series);
        List<DetectionPoint> points = new ArrayList<>();
        for (int i = WINDOW - 1; i < c2Points.size(); i++) {
            double sum = 0;
            for (int j = i - WINDOW + 1; j <= i; j++) {
                sum += Math.max(0, c2Points.get(j).score() - EXCESS_FLOOR);
            }
            points.add(new DetectionPoint(c2Points.get(i).weekStart(), sum, threshold, sum > threshold));
        }
        return points;
    }
}
