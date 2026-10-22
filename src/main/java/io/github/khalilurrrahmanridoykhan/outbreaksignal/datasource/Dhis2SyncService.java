package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.common.EpiWeek;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Observation;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.ObservationRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnit;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnitRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Series;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.SeriesRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pulls the configured DHIS2 data elements into {@code observation} and keeps
 * them there. See {@code docs/dhis2-spike.md} for why the newest weeks are
 * normally empty on the public demo, and DECISIONS.md #16-17 for why org
 * units are stored flat (no parent link) and why one failing data element
 * does not stop the others.
 *
 * <p>No explicit locking is taken around the find-then-save upserts below;
 * correctness relies on {@link Dhis2SyncScheduler}'s ShedLock guarantee that
 * only one instance ever runs a sync at a time. A human calling the manual
 * trigger at the same moment is the one unguarded race, and it is left to the
 * database's own unique constraints (surfaced as a 409, see
 * {@code ApiExceptionHandler}) rather than designed around, since it is rare
 * and the sync is safe to simply run again.
 */
@Service
public class Dhis2SyncService {

    private static final Logger log = LoggerFactory.getLogger(Dhis2SyncService.class);

    private final DhisClient dhisClient;
    private final OrgUnitRepository orgUnits;
    private final SeriesRepository series;
    private final ObservationRepository observations;
    private final OutbreakProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    Dhis2SyncService(
            DhisClient dhisClient,
            OrgUnitRepository orgUnits,
            SeriesRepository series,
            ObservationRepository observations,
            OutbreakProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.dhisClient = dhisClient;
        this.orgUnits = orgUnits;
        this.series = series;
        this.observations = observations;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public SyncResult sync() {
        Timer.Sample timer = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            OutbreakProperties.Dhis2 dhis2 = properties.dhis2();
            int orgUnitsCreated = syncOrgUnits(dhis2.orgUnitLevel());

            List<EpiWeek> weeks = EpiWeek.containing(LocalDate.now(clock))
                    .lastWeeksEndingHere(properties.sync().windowWeeks());

            List<SyncResult.DataElementResult> results = new ArrayList<>(dhis2.dataElements().size());
            for (OutbreakProperties.Dhis2.DataElement dataElement : dhis2.dataElements()) {
                results.add(syncDataElement(dataElement, dhis2.orgUnitLevel(), weeks));
            }

            SyncResult result = new SyncResult(orgUnitsCreated, weeks.size(), results);
            if (results.stream().anyMatch(r -> !r.succeeded())) {
                outcome = "partial-failure";
            }
            log.info("DHIS2 sync finished: {} org unit(s) created, {} week(s) requested per data element, {} data element(s) processed",
                    result.orgUnitsCreated(), result.weeksRequested(), result.dataElements().size());
            return result;
        } catch (RuntimeException e) {
            outcome = "failure";
            throw e;
        } finally {
            timer.stop(Timer.builder("outbreak.sync.duration")
                    .tag("outcome", outcome)
                    .description("How long one DHIS2 sync run took, and whether every data element succeeded")
                    .register(meterRegistry));
        }
    }

    private int syncOrgUnits(int level) {
        List<DhisOrgUnit> fetched = dhisClient.fetchOrgUnits(level);
        int created = 0;
        for (DhisOrgUnit dto : fetched) {
            if (orgUnits.findByDhis2Uid(dto.uid()).isEmpty()) {
                // parent is deliberately null; see DECISIONS.md #16.
                orgUnits.save(new OrgUnit(dto.uid(), dto.name(), dto.level(), null));
                created++;
            }
        }
        return created;
    }

    private SyncResult.DataElementResult syncDataElement(
            OutbreakProperties.Dhis2.DataElement dataElement, int level, List<EpiWeek> weeks) {
        try {
            List<DhisObservation> fetched = dhisClient.fetchWeeklyAnalytics(dataElement.uid(), level, weeks);
            int upserted = 0;
            for (DhisObservation observation : fetched) {
                Optional<OrgUnit> orgUnit = orgUnits.findByDhis2Uid(observation.orgUnitUid());
                if (orgUnit.isEmpty()) {
                    log.warn("Analytics reported a value for org unit {} which was not in the org-unit fetch; skipping",
                            observation.orgUnitUid());
                    continue;
                }
                Series matchedSeries = findOrCreateSeries(orgUnit.get(), dataElement);
                upsertObservation(matchedSeries, observation.week().weekStart(), observation.value());
                upserted++;
            }
            return SyncResult.DataElementResult.success(dataElement.uid(), dataElement.name(), upserted);
        } catch (DhisClientException e) {
            // One data element failing (DHIS2 retries already exhausted, or a
            // genuine 4xx) does not stop the others: the next scheduled run
            // retries it, and the sync is idempotent, so partial progress now
            // is strictly better than none.
            log.error("Sync failed for data element {} ({}): {}", dataElement.name(), dataElement.uid(), e.getMessage());
            Counter.builder("outbreak.sync.failures")
                    .tag("dataElement", dataElement.uid())
                    .description("DHIS2 sync failures, by data element")
                    .register(meterRegistry)
                    .increment();
            return SyncResult.DataElementResult.failed(dataElement.uid(), dataElement.name(), e.getMessage());
        }
    }

    private Series findOrCreateSeries(OrgUnit orgUnit, OutbreakProperties.Dhis2.DataElement dataElement) {
        return series.findByOrgUnitIdAndDhis2DataElementUid(orgUnit.getId(), dataElement.uid())
                .orElseGet(() -> series.save(new Series(orgUnit, dataElement.uid(), dataElement.name())));
    }

    private void upsertObservation(Series matchedSeries, LocalDate weekStart, BigDecimal value) {
        Optional<Observation> existing = observations.findBySeriesIdAndWeekStart(matchedSeries.getId(), weekStart);
        if (existing.isPresent()) {
            existing.get().updateValue(value);
            observations.save(existing.get());
        } else {
            observations.save(new Observation(matchedSeries, weekStart, value));
        }
    }
}
