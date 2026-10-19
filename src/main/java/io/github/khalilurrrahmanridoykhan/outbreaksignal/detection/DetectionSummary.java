package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.util.List;

/** What one call to {@link DetectionService#runDetection()} did, one entry per strategy run. */
public record DetectionSummary(List<DetectionRunSummary> runs) {

    public record DetectionRunSummary(String algorithm, int seriesEvaluated, int alertsCreated) {
    }
}
