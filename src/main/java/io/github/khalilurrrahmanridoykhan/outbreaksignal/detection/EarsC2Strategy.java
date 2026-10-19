package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CDC EARS method C2: the same baseline length as C1, but shifted back by a
 * fixed 2-week guard band — the baseline ends 2 weeks before the current
 * one, not immediately before it. That guard band is what C2 adds over C1,
 * so it is a fixed part of the algorithm here, not a tuning parameter.
 *
 * <p>{@link EarsC3Strategy} reuses this class directly for its own raw
 * z-scores rather than duplicating the guard-band arithmetic.
 */
public class EarsC2Strategy implements DetectionStrategy {

    static final int GUARD_WEEKS = 2;

    private final int baselineWeeks;
    private final double threshold;

    public EarsC2Strategy(OutbreakProperties.Detection.Ears config) {
        this.baselineWeeks = config.baselineWeeks();
        this.threshold = config.threshold();
    }

    @Override
    public String id() {
        return "EARS_C2";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("baselineWeeks", baselineWeeks, "guardWeeks", GUARD_WEEKS, "threshold", threshold);
    }

    @Override
    public List<DetectionPoint> evaluate(List<WeeklyValue> series) {
        List<DetectionPoint> points = new ArrayList<>();
        int firstEligible = baselineWeeks + GUARD_WEEKS;
        for (int i = firstEligible; i < series.size(); i++) {
            // Baseline ends GUARD_WEEKS before i, so the 2 weeks immediately
            // before the current one never contribute to it.
            int baselineEnd = i - GUARD_WEEKS; // exclusive
            double[] baseline = new double[baselineWeeks];
            for (int j = 0; j < baselineWeeks; j++) {
                baseline[j] = series.get(baselineEnd - baselineWeeks + j).value();
            }
            double current = series.get(i).value();
            double z = DetectionMath.zScore(current, baseline);
            points.add(new DetectionPoint(series.get(i).weekStart(), z, threshold, z > threshold));
        }
        return points;
    }
}
