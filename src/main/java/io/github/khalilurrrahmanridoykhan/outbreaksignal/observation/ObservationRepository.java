package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservationRepository extends JpaRepository<Observation, Long> {

    List<Observation> findBySeriesIdAndWeekStartGreaterThanEqualOrderByWeekStart(Long seriesId, LocalDate from);

    Optional<Observation> findBySeriesIdAndWeekStart(Long seriesId, LocalDate weekStart);

    /** A series' whole history, oldest first — what every detection strategy evaluates. */
    List<Observation> findBySeriesIdOrderByWeekStartAsc(Long seriesId);
}
