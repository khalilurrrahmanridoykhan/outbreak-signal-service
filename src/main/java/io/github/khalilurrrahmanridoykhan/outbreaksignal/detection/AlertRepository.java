package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findBySeriesIdAndPeriodStartAndAlgorithm(Long seriesId, LocalDate periodStart, String algorithm);

    /**
     * {@code GET /alerts}'s filters, each optional: a null parameter matches
     * everything for that column. Plain nullable-parameter JPQL rather than
     * the Specification API, consistent with the rest of this codebase's
     * explicit, dependency-light style.
     *
     * <p>{@code COALESCE(:param, a.column)} rather than
     * {@code :param IS NULL OR a.column = :param}: the latter gives
     * PostgreSQL's parameter-type inference nothing to work with for the
     * {@code IS NULL} occurrence (a bind parameter compared only to
     * {@code NULL} has no inferable type), which surfaces as
     * {@code could not determine data type of parameter $n} — not on every
     * call, only once a parameter actually binds a real, non-null value
     * (every previous test of this query happened to pass all five as
     * null). {@code COALESCE} needs only one occurrence of each named
     * parameter, always next to a column of known type, so there is always
     * a type to infer from.
     */
    @Query("""
            SELECT a FROM Alert a
            WHERE a.status = COALESCE(:status, a.status)
              AND a.series.orgUnit.id = COALESCE(:orgUnitId, a.series.orgUnit.id)
              AND a.algorithm = COALESCE(:algorithm, a.algorithm)
              AND a.series.name = COALESCE(:dataElementName, a.series.name)
              AND a.severity = COALESCE(:severity, a.severity)
              AND a.periodStart >= COALESCE(:from, a.periodStart)
              AND a.periodStart <= COALESCE(:to, a.periodStart)
            """)
    Page<Alert> search(
            @Param("status") AlertStatus status,
            @Param("orgUnitId") Long orgUnitId,
            @Param("algorithm") String algorithm,
            @Param("dataElementName") String dataElementName,
            @Param("severity") Severity severity,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);
}
