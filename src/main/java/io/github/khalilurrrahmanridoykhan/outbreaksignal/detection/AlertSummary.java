package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnit;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Series;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** What {@code GET /alerts} and the write endpoints return — an {@link Alert}, flattened for JSON. */
public record AlertSummary(
        Long id,
        Long orgUnitId,
        String orgUnitName,
        String dataElementName,
        String algorithm,
        LocalDate periodStart,
        BigDecimal score,
        BigDecimal threshold,
        Severity severity,
        AlertStatus status,
        long version,
        Instant createdAt) {

    static AlertSummary from(Alert alert) {
        Series series = alert.getSeries();
        OrgUnit orgUnit = series.getOrgUnit();
        return new AlertSummary(
                alert.getId(),
                orgUnit.getId(),
                orgUnit.getName(),
                series.getName(),
                alert.getAlgorithm(),
                alert.getPeriodStart(),
                alert.getScore(),
                alert.getThreshold(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getVersion(),
                alert.getCreatedAt());
    }
}
