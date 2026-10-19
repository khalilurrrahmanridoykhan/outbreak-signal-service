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
     */
    @Query("""
            SELECT a FROM Alert a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:orgUnitId IS NULL OR a.series.orgUnit.id = :orgUnitId)
              AND (:algorithm IS NULL OR a.algorithm = :algorithm)
              AND (:from IS NULL OR a.periodStart >= :from)
              AND (:to IS NULL OR a.periodStart <= :to)
            """)
    Page<Alert> search(
            @Param("status") AlertStatus status,
            @Param("orgUnitId") Long orgUnitId,
            @Param("algorithm") String algorithm,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);
}
