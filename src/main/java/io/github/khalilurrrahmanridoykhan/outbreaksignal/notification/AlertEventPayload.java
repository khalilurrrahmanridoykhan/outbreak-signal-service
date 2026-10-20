package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertSummary;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The alert snapshot stored as {@link OutboxEvent#getPayload()}. Built from
 * {@link AlertSummary} — already resolved, lazy-association-free — rather
 * than from {@code Alert} directly, so this package never needs its own copy
 * of that resolution logic and can never reintroduce the lazy-loading bug it
 * fixes (see DECISIONS.md).
 */
public record AlertEventPayload(
        Long alertId,
        Long orgUnitId,
        String orgUnitName,
        String dataElementName,
        String algorithm,
        LocalDate periodStart,
        BigDecimal score,
        BigDecimal threshold,
        Severity severity,
        AlertStatus status,
        String note,
        String actor) {

    public static AlertEventPayload from(AlertSummary summary, String note, String actor) {
        return new AlertEventPayload(
                summary.id(),
                summary.orgUnitId(),
                summary.orgUnitName(),
                summary.dataElementName(),
                summary.algorithm(),
                summary.periodStart(),
                summary.score(),
                summary.threshold(),
                summary.severity(),
                summary.status(),
                note,
                actor);
    }
}
