package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.TestcontainersConfiguration;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.notification.OutboxEventRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnit;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnitRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Series;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.SeriesRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exercises {@link AlertService} and {@link Alert}'s optimistic locking
 * against a real PostgreSQL. The concurrent-review scenario is reproduced
 * deterministically — two separately loaded copies of the same alert, one
 * saved before the other — rather than with real threads: optimistic
 * locking is detected at save time by a version mismatch regardless of
 * timing, so this is the same scenario a real race would produce, without a
 * flaky test that depends on getting two threads' timing exactly right.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AlertServiceIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);
    private static final String ANALYST = "analyst@example.org";

    @Autowired
    private AlertService alertService;
    @Autowired
    private AlertRepository alerts;
    @Autowired
    private AlertEventRepository events;
    @Autowired
    private DetectionRunRepository detectionRuns;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private OrgUnitRepository orgUnits;
    @Autowired
    private OutboxEventRepository outboxEvents;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Alert openAlert;

    @BeforeEach
    void seedAnOpenAlert() {
        OrgUnit orgUnit = orgUnits.save(new OrgUnit("QwMiPiME3bA", "Kpanga Kabonde", 3, null));
        Series series = seriesRepository.save(new Series(orgUnit, "vq2qO3eTrNi", "IDSR Malaria"));
        DetectionRun run = detectionRuns.save(new DetectionRun("EARS_C1", "{}", 104, Instant.now()));
        run.finish(Instant.now(), 1, 1);
        detectionRuns.save(run);
        openAlert = alerts.save(new Alert(series, MONDAY, "EARS_C1", new BigDecimal("9.5"), new BigDecimal("3.0"),
                Severity.HIGH, run, Instant.now()));
    }

    @AfterEach
    void cleanUp() {
        outboxEvents.deleteAll();
        events.deleteAll();
        alerts.deleteAll();
        detectionRuns.deleteAll();
        seriesRepository.deleteAll();
        orgUnits.deleteAll();
    }

    @Test
    void acknowledgeThenResolveWritesOneEventPerMoveAndEndsResolved() {
        alertService.acknowledge(openAlert.getId(), ANALYST);
        AlertSummary resolved = alertService.resolve(openAlert.getId(), ANALYST);

        assertThat(resolved.status()).isEqualTo(AlertStatus.RESOLVED);

        List<AlertEventSummary> history = alertService.getDetail(openAlert.getId()).history();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).fromStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(history.get(0).toStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(history.get(1).fromStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(history.get(1).toStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(history).allSatisfy(event -> assertThat(event.actor()).isEqualTo(ANALYST));
        // One outbox row per transition, same as the alert_event trail above.
        assertThat(outboxEvents.count()).isEqualTo(2);
    }

    @Test
    void resolvingAnOpenAlertDirectlyIsRejectedWithoutWritingAnyEvent() {
        assertThatThrownBy(() -> alertService.resolve(openAlert.getId(), ANALYST))
                .isInstanceOf(IllegalAlertTransitionException.class);

        AlertDetail detail = alertService.getDetail(openAlert.getId());
        assertThat(detail.alert().status()).isEqualTo(AlertStatus.OPEN);
        assertThat(detail.history()).isEmpty();
        assertThat(outboxEvents.count()).isZero();
    }

    /**
     * {@link AlertEventPublisher#publish} runs with
     * {@code Propagation.MANDATORY}, so it joins {@code acknowledge}'s own
     * transaction rather than opening a separate one; wrapping the whole
     * call in an outer transaction and forcing that to roll back (instead of
     * throwing from inside the state machine, which the test above already
     * covers) proves the outbox row is not just conditionally written, but
     * genuinely atomic with the alert change and its {@code alert_event} —
     * the plan's own "force a rollback and assert no outbox row".
     */
    @Test
    void aForcedRollbackAfterAnOtherwiseSuccessfulAcknowledgeLeavesNoOutboxRowEither() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            alertService.acknowledge(openAlert.getId(), ANALYST);
            throw new RuntimeException("forced rollback after an otherwise successful transition");
        })).hasMessageContaining("forced rollback");

        assertThat(alerts.findById(openAlert.getId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(events.findByAlertIdOrderByCreatedAtAsc(openAlert.getId())).isEmpty();
        assertThat(outboxEvents.count()).isZero();
    }

    @Test
    void dismissingWithoutAReasonIsRejected() {
        assertThatThrownBy(() -> alertService.dismiss(openAlert.getId(), "   ", ANALYST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> alertService.dismiss(openAlert.getId(), null, ANALYST))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(alertService.getDetail(openAlert.getId()).alert().status()).isEqualTo(AlertStatus.OPEN);
    }

    @Test
    void dismissingWithAReasonRecordsThatReasonOnTheEvent() {
        alertService.dismiss(openAlert.getId(), "Reviewed: reporting delay, not a real rise", ANALYST);

        List<AlertEventSummary> history = alertService.getDetail(openAlert.getId()).history();
        assertThat(history).singleElement().satisfies(event ->
                assertThat(event.note()).isEqualTo("Reviewed: reporting delay, not a real rise"));
    }

    @Test
    void savingAStaleCopyAfterAConcurrentUpdateFailsWithAnOptimisticLockException() {
        Alert firstLoadedCopy = alerts.findById(openAlert.getId()).orElseThrow();
        Alert secondLoadedCopy = alerts.findById(openAlert.getId()).orElseThrow();

        firstLoadedCopy.transitionTo(AlertStatus.ACKNOWLEDGED);
        alerts.saveAndFlush(firstLoadedCopy);

        secondLoadedCopy.transitionTo(AlertStatus.DISMISSED); // legal per the state machine; the version is what's stale
        assertThatThrownBy(() -> alerts.saveAndFlush(secondLoadedCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(alertService.getDetail(openAlert.getId()).alert().status()).isEqualTo(AlertStatus.ACKNOWLEDGED);
    }

    /**
     * Regression test for a real bug this phase's live verification caught:
     * {@code AlertSummary.from} reads {@code Series}/{@code OrgUnit}, both
     * lazy associations; mapping them outside the loading transaction threw
     * {@code LazyInitializationException} on every real (non-mocked) alert,
     * never on a WebMvcTest's hand-built one — see DECISIONS.md.
     */
    @Test
    void searchAndDetailResolveTheLazyOrgUnitAndSeriesNamesWithoutError() {
        AlertSummary fromSearch = alertService.search(null, null, null, null, null, null, null, Pageable.unpaged())
                .getContent().getFirst();
        assertThat(fromSearch.orgUnitName()).isEqualTo("Kpanga Kabonde");
        assertThat(fromSearch.dataElementName()).isEqualTo("IDSR Malaria");

        AlertSummary fromDetail = alertService.getDetail(openAlert.getId()).alert();
        assertThat(fromDetail.orgUnitName()).isEqualTo("Kpanga Kabonde");
        assertThat(fromDetail.dataElementName()).isEqualTo("IDSR Malaria");
    }

    @Test
    void searchFiltersByDataElementNameAndSeverityIndependently() {
        Series choleraSeries = seriesRepository.save(new Series(openAlert.getSeries().getOrgUnit(), "UsSUX0cpKsH", "IDSR Cholera"));
        DetectionRun run = detectionRuns.save(new DetectionRun("CUSUM", "{}", 104, Instant.now()));
        run.finish(Instant.now(), 1, 1);
        detectionRuns.save(run);
        Alert lowCholeraAlert = alerts.save(new Alert(choleraSeries, MONDAY.plusWeeks(1), "CUSUM",
                new BigDecimal("3.5"), new BigDecimal("3.0"), Severity.LOW, run, Instant.now()));

        // openAlert itself is IDSR Malaria / HIGH (see seedAnOpenAlert) -- the
        // contrast with this new IDSR Cholera / LOW alert is what proves each
        // filter is independent of the other, not just "whatever's HIGH".
        assertThat(alertService.search(null, null, null, "IDSR Cholera", null, null, null, Pageable.unpaged())
                .getContent())
                .extracting(AlertSummary::id)
                .containsExactly(lowCholeraAlert.getId());

        assertThat(alertService.search(null, null, null, null, Severity.LOW, null, null, Pageable.unpaged())
                .getContent())
                .extracting(AlertSummary::id)
                .containsExactly(lowCholeraAlert.getId());

        assertThat(alertService.search(null, null, null, null, Severity.HIGH, null, null, Pageable.unpaged())
                .getContent())
                .extracting(AlertSummary::id)
                .containsExactly(openAlert.getId());
    }
}
