package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.util.List;
import java.util.Map;

/**
 * One aberration-detection algorithm. Every implementation is pure: given the
 * same series twice, it returns the same result twice, which is what lets
 * {@code DetectionService} re-run detection over a series' whole history on
 * every sync without that being wasteful or risky — see
 * {@code docs/dhis2-spike.md} and DECISIONS.md for why re-evaluation, not
 * "only the newest week," is how this project keeps detection idempotent.
 */
public interface DetectionStrategy {

    /** A short, stable identifier, stored as {@code alert.algorithm} and {@code detection_run.algorithm}. */
    String id();

    /** This instance's configuration, recorded on {@code detection_run} so every alert stays reproducible. */
    Map<String, Object> parameters();

    /**
     * Evaluates every week in {@code series} (oldest first) that has enough
     * preceding history to be scored. The result is in the same order as the
     * input and is never longer than it; a week needing more baseline than
     * came before it is simply absent, not scored with a short baseline.
     */
    List<DetectionPoint> evaluate(List<WeeklyValue> series);
}
