package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Builds a {@code List<WeeklyValue>} from plain numbers, one week apart, for hand-computed fixtures. */
final class TestSeries {

    private static final LocalDate FIRST_MONDAY = LocalDate.of(2026, 1, 5);

    private TestSeries() {
    }

    static List<WeeklyValue> of(double... values) {
        List<WeeklyValue> series = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            series.add(new WeeklyValue(FIRST_MONDAY.plusWeeks(i), values[i]));
        }
        return series;
    }

    static List<WeeklyValue> scaledBy(List<WeeklyValue> series, double factor) {
        List<WeeklyValue> scaled = new ArrayList<>(series.size());
        for (WeeklyValue point : series) {
            scaled.add(new WeeklyValue(point.weekStart(), point.value() * factor));
        }
        return scaled;
    }
}
