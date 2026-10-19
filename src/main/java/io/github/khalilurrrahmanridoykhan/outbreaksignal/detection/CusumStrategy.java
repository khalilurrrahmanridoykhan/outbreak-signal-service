package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A one-sided CUSUM. Unlike the EARS and moving-average strategies, the
 * reference mean and standard deviation are estimated <em>once</em>, from
 * the series' first {@code baselineWeeks} weeks, and never re-estimated —
 * that fixed reference, accumulated against week after week, is what gives
 * CUSUM its sensitivity to a small sustained shift that no single week's
 * z-score would cross a threshold on its own.
 */
public class CusumStrategy implements DetectionStrategy {

    private final int baselineWeeks;
    private final double referenceKSigma;
    private final double thresholdHSigma;

    public CusumStrategy(OutbreakProperties.Detection.Cusum config) {
        this.baselineWeeks = config.baselineWeeks();
        this.referenceKSigma = config.referenceKSigma();
        this.thresholdHSigma = config.thresholdHSigma();
    }

    @Override
    public String id() {
        return "CUSUM";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("baselineWeeks", baselineWeeks, "referenceKSigma", referenceKSigma, "thresholdHSigma", thresholdHSigma);
    }

    @Override
    public List<DetectionPoint> evaluate(List<WeeklyValue> series) {
        List<DetectionPoint> points = new ArrayList<>();
        if (series.size() <= baselineWeeks) {
            return points;
        }
        double[] baseline = new double[baselineWeeks];
        for (int j = 0; j < baselineWeeks; j++) {
            baseline[j] = series.get(j).value();
        }
        double mean = DetectionMath.mean(baseline);
        double sd = DetectionMath.sampleStandardDeviation(baseline, mean);
        double referenceK = referenceKSigma * sd;
        double thresholdH = thresholdHSigma * sd;

        double cumulative = 0;
        for (int i = baselineWeeks; i < series.size(); i++) {
            double current = series.get(i).value();
            cumulative = Math.max(0, cumulative + (current - mean) - referenceK);
            points.add(new DetectionPoint(series.get(i).weekStart(), cumulative, thresholdH, cumulative > thresholdH));
        }
        return points;
    }
}
