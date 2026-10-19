package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class SeriesService {

    private final SeriesRepository seriesRepository;
    private final ObservationRepository observationRepository;

    SeriesService(SeriesRepository seriesRepository, ObservationRepository observationRepository) {
        this.seriesRepository = seriesRepository;
        this.observationRepository = observationRepository;
    }

    public Page<Series> list(Pageable pageable) {
        return seriesRepository.findAll(pageable);
    }

    public Series get(Long id) {
        return seriesRepository.findById(id).orElseThrow(() -> new SeriesNotFoundException(id));
    }

    public List<Observation> observations(Long id) {
        get(id); // 404 if the series itself does not exist, rather than an empty list for it
        return observationRepository.findBySeriesIdOrderByWeekStartAsc(id);
    }
}
