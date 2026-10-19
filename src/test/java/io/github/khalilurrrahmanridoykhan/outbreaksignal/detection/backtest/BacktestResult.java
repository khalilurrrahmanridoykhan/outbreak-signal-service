package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.backtest;

/**
 * One strategy's measured performance across every replicate. See
 * {@link BacktestEvaluator} for exactly what each number means and why
 * {@code falseAlarmWeeksPer100} and {@code falseAlarmEpisodesPer100} can
 * differ a lot for a strategy whose statistic decays slowly after a real
 * event.
 */
record BacktestResult(
        String algorithm,
        double sensitivity,
        double falseAlarmWeeksPer100,
        double falseAlarmEpisodesPer100,
        Double medianTimelinessWeeks,
        int replicates) {
}
