package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findBySeriesIdAndPeriodStartAndAlgorithm(Long seriesId, LocalDate periodStart, String algorithm);
}
