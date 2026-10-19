package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** What {@code GET /series/{id}/observations} returns — one {@link Observation}, flattened for JSON. */
public record ObservationSummary(LocalDate weekStart, BigDecimal value, Instant fetchedAt) {

    static ObservationSummary from(Observation observation) {
        return new ObservationSummary(observation.getWeekStart(), observation.getValue(), observation.getFetchedAt());
    }
}
