package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import tools.jackson.databind.ObjectMapper;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.notification.AlertEventPayload;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.notification.AlertEventPublisher;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Observation;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.ObservationRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Series;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.SeriesRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs every configured {@link DetectionStrategy} over every series' whole
 * history. Re-running it, with the same observations unchanged, creates no
 * new alerts and changes nothing — the same idempotence this project already
 * relies on for syncing (see DECISIONS.md #19), achieved the same way: an
 * alert is created only if one does not already exist for its
 * (series, period, algorithm), and an existing one is never touched. That
 * also means a human's review of an alert (once Phase 4 adds it) can never
 * be silently reset by a later detection run re-scoring the same week.
 */
@Service
public class DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    private final List<DetectionStrategy> strategies;
    private final SeriesRepository seriesRepository;
    private final ObservationRepository observationRepository;
    private final DetectionRunRepository detectionRunRepository;
    private final AlertRepository alertRepository;
    private final AlertEventPublisher outbox;
    private final OutbreakProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DetectionService(
            List<DetectionStrategy> strategies,
            SeriesRepository seriesRepository,
            ObservationRepository observationRepository,
            DetectionRunRepository detectionRunRepository,
            AlertRepository alertRepository,
            AlertEventPublisher outbox,
            OutbreakProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.strategies = strategies;
        this.seriesRepository = seriesRepository;
        this.observationRepository = observationRepository;
        this.detectionRunRepository = detectionRunRepository;
        this.alertRepository = alertRepository;
        this.outbox = outbox;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public DetectionSummary runDetection() {
        List<Series> allSeries = seriesRepository.findAll();
        List<DetectionSummary.DetectionRunSummary> summaries = new ArrayList<>(strategies.size());
        for (DetectionStrategy strategy : strategies) {
            summaries.add(runOneStrategy(strategy, allSeries));
        }
        return new DetectionSummary(summaries);
    }

    private DetectionSummary.DetectionRunSummary runOneStrategy(DetectionStrategy strategy, List<Series> allSeries) {
        Instant startedAt = clock.instant();
        DetectionRun run = detectionRunRepository.save(new DetectionRun(
                strategy.id(), toJson(strategy.parameters()), properties.sync().windowWeeks(), startedAt));

        int seriesEvaluated = 0;
        int alertsCreated = 0;
        for (Series series : allSeries) {
            List<Observation> observations = observationRepository.findBySeriesIdOrderByWeekStartAsc(series.getId());
            if (observations.isEmpty()) {
                continue;
            }
            seriesEvaluated++;
            List<WeeklyValue> weeklyValues = observations.stream()
                    .map(o -> new WeeklyValue(o.getWeekStart(), o.getValue().doubleValue()))
                    .toList();
            for (DetectionPoint point : strategy.evaluate(weeklyValues)) {
                if (point.alarmed() && createAlertIfAbsent(series, strategy, point, run)) {
                    alertsCreated++;
                }
            }
        }

        run.finish(clock.instant(), seriesEvaluated, alertsCreated);
        detectionRunRepository.save(run);
        log.info("Detection run finished: algorithm={} seriesEvaluated={} alertsCreated={}",
                strategy.id(), seriesEvaluated, alertsCreated);
        return new DetectionSummary.DetectionRunSummary(strategy.id(), seriesEvaluated, alertsCreated);
    }

    /** @return true if a new alert was created; false if one already existed and was left untouched. */
    private boolean createAlertIfAbsent(Series series, DetectionStrategy strategy, DetectionPoint point, DetectionRun run) {
        boolean exists = alertRepository
                .findBySeriesIdAndPeriodStartAndAlgorithm(series.getId(), point.weekStart(), strategy.id())
                .isPresent();
        if (exists) {
            return false;
        }
        Severity severity = Severity.fromScoreOverThreshold(point.score(), point.threshold());
        Instant now = clock.instant();
        Alert saved = alertRepository.save(new Alert(
                series,
                point.weekStart(),
                strategy.id(),
                BigDecimal.valueOf(point.score()),
                BigDecimal.valueOf(point.threshold()),
                severity,
                run,
                now));
        outbox.publish(AlertEventPayload.from(AlertSummary.from(saved), null, null), now);
        return true;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // Parameters are plain strings, ints and doubles (see every
            // strategy's parameters()); this can only fail if that ever
            // changes to something Jackson cannot serialize, which is a
            // programming error worth failing loudly on, not swallowing.
            throw new IllegalStateException("Could not serialise detection parameters for " + value, e);
        }
    }
}
