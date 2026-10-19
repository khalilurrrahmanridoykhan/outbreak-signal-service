package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.backtest;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.WeeklyValue;
import java.util.List;

/**
 * One generated series plus the ground truth the generator used to build
 * it, so the evaluator can grade a strategy's alarms against what is
 * actually true, which real surveillance data never comes labelled with.
 */
record SyntheticSeries(List<WeeklyValue> series, int outbreakStartIndex, int outbreakDurationWeeks) {

    boolean isWithin(int weekIndex, int graceWeeks) {
        return weekIndex >= outbreakStartIndex && weekIndex < outbreakStartIndex + outbreakDurationWeeks + graceWeeks;
    }
}
