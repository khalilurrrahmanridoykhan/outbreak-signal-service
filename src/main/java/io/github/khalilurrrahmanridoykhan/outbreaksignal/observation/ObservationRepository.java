package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservationRepository extends JpaRepository<Observation, Long> {

    List<Observation> findBySeriesIdAndWeekStartGreaterThanEqualOrderByWeekStart(Long seriesId, LocalDate from);
}
