package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The simplest strategy, kept as a baseline for comparing the others against:
 * flag a week whose value is more than {@code thresholdK} standard
 * deviations above the mean of the {@code baselineWeeks} weeks before it.
 */
public class MovingAverageZStrategy implements DetectionStrategy {

    private final int baselineWeeks;
    private final double thresholdK;

    public MovingAverageZStrategy(OutbreakProperties.Detection.MovingAverage config) {
        this.baselineWeeks = config.baselineWeeks();
        this.thresholdK = config.thresholdK();
    }

    @Override
    public String id() {
        return "MOVING_AVERAGE_Z";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("baselineWeeks", baselineWeeks, "thresholdK", thresholdK);
    }

    @Override
    public List<DetectionPoint> evaluate(List<WeeklyValue> series) {
        List<DetectionPoint> points = new ArrayList<>();
        for (int i = baselineWeeks; i < series.size(); i++) {
            double[] baseline = new double[baselineWeeks];
            for (int j = 0; j < baselineWeeks; j++) {
                baseline[j] = series.get(i - baselineWeeks + j).value();
            }
            double current = series.get(i).value();
            double z = DetectionMath.zScore(current, baseline);
            points.add(new DetectionPoint(series.get(i).weekStart(), z, thresholdK, z > thresholdK));
        }
        return points;
    }
}
