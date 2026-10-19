package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.TestcontainersConfiguration;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnit;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnitRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Series;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.SeriesRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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
        events.deleteAll();
        alerts.deleteAll();
        detectionRuns.deleteAll();
        seriesRepository.deleteAll();
        orgUnits.deleteAll();
    }

    @Test
    void acknowledgeThenResolveWritesOneEventPerMoveAndEndsResolved() {
        alertService.acknowledge(openAlert.getId(), ANALYST);
        Alert resolved = alertService.resolve(openAlert.getId(), ANALYST);

        assertThat(resolved.getStatus()).isEqualTo(AlertStatus.RESOLVED);

        var history = alertService.history(openAlert.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getFromStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(history.get(0).getToStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(history.get(1).getFromStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(history.get(1).getToStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(history).allSatisfy(event -> assertThat(event.getActor()).isEqualTo(ANALYST));
    }

    @Test
    void resolvingAnOpenAlertDirectlyIsRejectedWithoutWritingAnyEvent() {
        assertThatThrownBy(() -> alertService.resolve(openAlert.getId(), ANALYST))
                .isInstanceOf(IllegalAlertTransitionException.class);

        assertThat(alertService.get(openAlert.getId()).getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(alertService.history(openAlert.getId())).isEmpty();
    }

    @Test
    void dismissingWithoutAReasonIsRejected() {
        assertThatThrownBy(() -> alertService.dismiss(openAlert.getId(), "   ", ANALYST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> alertService.dismiss(openAlert.getId(), null, ANALYST))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(alertService.get(openAlert.getId()).getStatus()).isEqualTo(AlertStatus.OPEN);
    }

    @Test
    void dismissingWithAReasonRecordsThatReasonOnTheEvent() {
        alertService.dismiss(openAlert.getId(), "Reviewed: reporting delay, not a real rise", ANALYST);

        var history = alertService.history(openAlert.getId());
        assertThat(history).singleElement().satisfies(event ->
                assertThat(event.getNote()).isEqualTo("Reviewed: reporting delay, not a real rise"));
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

        assertThat(alertService.get(openAlert.getId()).getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
    }
}
