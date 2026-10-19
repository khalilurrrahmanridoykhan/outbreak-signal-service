package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/series")
class SeriesController {

    private final SeriesService seriesService;

    SeriesController(SeriesService seriesService) {
        this.seriesService = seriesService;
    }

    @GetMapping
    Page<SeriesSummary> list(@PageableDefault(size = 20) Pageable pageable) {
        return seriesService.list(pageable);
    }

    @GetMapping("/{id}/observations")
    List<ObservationSummary> observations(@PathVariable Long id) {
        return seriesService.observations(id);
    }
}
