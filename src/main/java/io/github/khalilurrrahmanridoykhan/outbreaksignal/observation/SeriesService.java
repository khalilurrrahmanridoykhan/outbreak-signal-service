package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Always returns plain DTOs, never the JPA entities: {@code OrgUnit} is a
 * lazy association on {@link Series} (and {@code spring.jpa.open-in-view}
 * is deliberately off), so {@link SeriesSummary#from} has to run here,
 * inside this method's own transaction — mapping it later, after the
 * session that could load it is closed, throws
 * {@code LazyInitializationException} (see {@code detection.AlertService}'s
 * own Javadoc for the same fix, found the same way).
 */
@Service
public class SeriesService {

    private final SeriesRepository seriesRepository;
    private final ObservationRepository observationRepository;

    SeriesService(SeriesRepository seriesRepository, ObservationRepository observationRepository) {
        this.seriesRepository = seriesRepository;
        this.observationRepository = observationRepository;
    }

    @Transactional(readOnly = true)
    public Page<SeriesSummary> list(Pageable pageable) {
        return seriesRepository.findAll(pageable).map(SeriesSummary::from);
    }

    @Transactional(readOnly = true)
    public List<ObservationSummary> observations(Long id) {
        if (!seriesRepository.existsById(id)) {
            throw new SeriesNotFoundException(id);
        }
        return observationRepository.findBySeriesIdOrderByWeekStartAsc(id).stream()
                .map(ObservationSummary::from)
                .toList();
    }
}
