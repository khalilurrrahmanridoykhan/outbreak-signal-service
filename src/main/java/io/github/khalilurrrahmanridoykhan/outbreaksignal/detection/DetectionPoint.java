package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.time.LocalDate;

/**
 * What a strategy made of one week: the statistic it computed, the threshold
 * it compared that statistic against, and whether it crossed it. A week with
 * too little baseline history to evaluate simply has no {@code DetectionPoint}
 * at all, rather than one with a meaningless score.
 */
public record DetectionPoint(LocalDate weekStart, double score, double threshold, boolean alarmed) {
}
