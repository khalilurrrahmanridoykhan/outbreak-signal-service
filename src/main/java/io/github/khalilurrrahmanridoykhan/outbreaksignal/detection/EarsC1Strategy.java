package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CDC EARS method C1: the classic short baseline (7 weeks by default) taken
 * immediately before the current week, no guard band. Sensitive to sudden
 * rises; also the most easily contaminated if an outbreak has already begun
 * ramping up during those 7 weeks — that is what C2 guards against.
 */
class EarsC1Strategy implements DetectionStrategy {

    private final int baselineWeeks;
    private final double threshold;

    EarsC1Strategy(OutbreakProperties.Detection.Ears config) {
        this.baselineWeeks = config.baselineWeeks();
        this.threshold = config.threshold();
    }

    @Override
    public String id() {
        return "EARS_C1";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("baselineWeeks", baselineWeeks, "threshold", threshold);
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
            points.add(new DetectionPoint(series.get(i).weekStart(), z, threshold, z > threshold));
        }
        return points;
    }
}
