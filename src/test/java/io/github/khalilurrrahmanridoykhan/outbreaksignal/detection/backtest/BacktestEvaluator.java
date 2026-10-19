package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.backtest;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.DetectionPoint;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.DetectionStrategy;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Runs every strategy over many independent synthetic replicates, each with
 * exactly one injected outbreak at a known week, and grades each strategy's
 * alarms against that ground truth.
 *
 * <p>A strategy's alarm counts as a true detection if it falls within the
 * outbreak's own duration or the {@code graceWeeks} immediately after it
 * (real detection lags the true start by design — CUSUM and C3 especially,
 * since both accumulate evidence over several weeks); every alarm outside
 * that window, on any replicate, counts as a false alarm.
 *
 * <p>Two false-alarm numbers are reported. {@code falseAlarmWeeksPer100}
 * counts every alarmed week outside the window; {@code
 * falseAlarmEpisodesPer100} collapses a run of consecutive alarmed weeks
 * into one. They can differ a great deal for a strategy whose statistic
 * takes many weeks to fall back below its threshold after a real event —
 * CUSUM does, visibly, in this backtest (see {@code RESULTS.md}) — in which
 * case the per-week number overstates how often it is wrong about something
 * new, rather than still reacting to something real it already caught.
 */
final class BacktestEvaluator {

    private BacktestEvaluator() {
    }

    static List<BacktestResult> evaluate(
            List<DetectionStrategy> strategies,
            int replicates,
            long seed,
            int totalWeeks,
            int outbreakStartIndex,
            int outbreakDurationWeeks,
            double baselineMean,
            double seasonalAmplitude,
            double outbreakMagnitude,
            int graceWeeks) {
        Random random = new Random(seed);

        Map<String, Integer> detected = new HashMap<>();
        Map<String, List<Integer>> delays = new HashMap<>();
        Map<String, Integer> falseAlarmWeeks = new HashMap<>();
        Map<String, Integer> falseAlarmEpisodes = new HashMap<>();
        Map<String, Integer> eligibleNonOutbreakWeeks = new HashMap<>();
        for (DetectionStrategy strategy : strategies) {
            detected.put(strategy.id(), 0);
            delays.put(strategy.id(), new ArrayList<>());
            falseAlarmWeeks.put(strategy.id(), 0);
            falseAlarmEpisodes.put(strategy.id(), 0);
            eligibleNonOutbreakWeeks.put(strategy.id(), 0);
        }

        for (int r = 0; r < replicates; r++) {
            SyntheticSeries synthetic = SyntheticSeriesGenerator.generate(
                    random, totalWeeks, outbreakStartIndex, outbreakDurationWeeks,
                    baselineMean, seasonalAmplitude, outbreakMagnitude);
            LocalDate firstWeek = synthetic.series().getFirst().weekStart();

            for (DetectionStrategy strategy : strategies) {
                boolean strategyDetectedThisReplicate = false;
                boolean previousWeekWasAFalseAlarm = false;
                for (DetectionPoint point : strategy.evaluate(synthetic.series())) {
                    int weekIndex = (int) firstWeek.until(point.weekStart(), ChronoUnit.WEEKS);
                    boolean withinOutbreakWindow = synthetic.isWithin(weekIndex, graceWeeks);
                    if (point.alarmed()) {
                        if (withinOutbreakWindow) {
                            if (!strategyDetectedThisReplicate) {
                                strategyDetectedThisReplicate = true;
                                delays.get(strategy.id()).add(weekIndex - outbreakStartIndex);
                            }
                        } else {
                            falseAlarmWeeks.merge(strategy.id(), 1, Integer::sum);
                            if (!previousWeekWasAFalseAlarm) {
                                falseAlarmEpisodes.merge(strategy.id(), 1, Integer::sum);
                            }
                        }
                    }
                    if (!withinOutbreakWindow) {
                        previousWeekWasAFalseAlarm = point.alarmed();
                        eligibleNonOutbreakWeeks.merge(strategy.id(), 1, Integer::sum);
                    }
                }
                if (strategyDetectedThisReplicate) {
                    detected.merge(strategy.id(), 1, Integer::sum);
                }
            }
        }

        // Each strategy's own denominator — every week outside the
        // outbreak-plus-grace window that it actually produced a
        // DetectionPoint for — is tracked separately rather than shared,
        // since a longer baseline requirement (moving-average's 12 weeks
        // versus CUSUM's 8, say) means a strategy simply has no point at
        // all for a replicate's earliest weeks, in every replicate.
        List<BacktestResult> results = new ArrayList<>(strategies.size());
        for (DetectionStrategy strategy : strategies) {
            double sensitivity = detected.get(strategy.id()) / (double) replicates;
            int eligible = eligibleNonOutbreakWeeks.get(strategy.id());
            double faWeeks = 100.0 * falseAlarmWeeks.get(strategy.id()) / eligible;
            double faEpisodes = 100.0 * falseAlarmEpisodes.get(strategy.id()) / eligible;
            List<Integer> strategyDelays = delays.get(strategy.id());
            Double median = strategyDelays.isEmpty() ? null : median(strategyDelays);
            results.add(new BacktestResult(strategy.id(), sensitivity, faWeeks, faEpisodes, median, replicates));
        }
        return results;
    }

    private static double median(List<Integer> values) {
        List<Integer> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
