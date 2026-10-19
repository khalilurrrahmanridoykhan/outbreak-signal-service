package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.TestcontainersConfiguration;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Observation;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.ObservationRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnit;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnitRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Series;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.SeriesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Exercises the full wiring — configuration, every {@link DetectionStrategy}
 * bean, {@link DetectionService}, the JPA repositories, and a real
 * PostgreSQL — against a seeded series with an obvious, sustained rise. This
 * is what proves detection is idempotent the same way the sync already is:
 * running it twice over an unchanged series creates no second round of
 * alerts.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DetectionServiceIntegrationTest {

    private static final LocalDate FIRST_MONDAY = LocalDate.of(2026, 1, 5);

    @Autowired
    private DetectionService detectionService;
    @Autowired
    private OrgUnitRepository orgUnits;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private ObservationRepository observations;
    @Autowired
    private DetectionRunRepository detectionRuns;
    @Autowired
    private AlertRepository alerts;

    private Series series;

    @BeforeEach
    void seedASeriesWithASustainedRise() {
        OrgUnit orgUnit = orgUnits.save(new OrgUnit("QwMiPiME3bA", "Kpanga Kabonde", 3, null));
        series = seriesRepository.save(new Series(orgUnit, "vq2qO3eTrNi", "IDSR Malaria"));

        double[] values = {
                // 12 weeks of ordinary, slightly noisy baseline
                10, 11, 9, 10, 12, 9, 11, 10, 10, 11, 9, 10,
                // then a sustained, strong rise
                15, 18, 22, 26, 30, 34, 38, 42,
        };
        for (int i = 0; i < values.length; i++) {
            observations.save(new Observation(series, FIRST_MONDAY.plusWeeks(i), BigDecimal.valueOf(values[i])));
        }
    }

    @AfterEach
    void cleanUp() {
        alerts.deleteAll();
        detectionRuns.deleteAll();
        observations.deleteAll();
        seriesRepository.deleteAll();
        orgUnits.deleteAll();
    }

    @Test
    void everyStrategyRunsAndTheSustainedRiseProducesAlerts() {
        DetectionSummary summary = detectionService.runDetection();

        assertThat(summary.runs()).extracting("algorithm")
                .containsExactlyInAnyOrder("MOVING_AVERAGE_Z", "EARS_C1", "EARS_C2", "EARS_C3", "CUSUM");
        assertThat(summary.runs()).allSatisfy(run -> assertThat(run.seriesEvaluated()).isEqualTo(1));
        assertThat(summary.runs()).allSatisfy(run -> assertThat(run.alertsCreated()).isGreaterThan(0));

        assertThat(detectionRuns.findAll()).hasSize(5);
        assertThat(detectionRuns.findAll()).allSatisfy(run -> {
            assertThat(run.getFinishedAt()).isNotNull();
            assertThat(run.getParamsJson()).isNotBlank().contains("{");
        });

        List<Alert> stored = alerts.findAll();
        assertThat(stored).isNotEmpty();
        assertThat(stored).allSatisfy(alert -> {
            assertThat(alert.getStatus()).isEqualTo(AlertStatus.OPEN);
            assertThat(alert.getSeverity()).isNotNull();
            assertThat(alert.getScore()).isGreaterThan(alert.getThreshold());
            // The rise starts at week index 12; nothing before it should ever alarm.
            assertThat(alert.getPeriodStart()).isAfterOrEqualTo(FIRST_MONDAY.plusWeeks(12));
        });
    }

    @Test
    void runningDetectionTwiceCreatesNoSecondRoundOfAlerts() {
        detectionService.runDetection();
        int alertsAfterFirstRun = alerts.findAll().size();

        DetectionSummary second = detectionService.runDetection();

        assertThat(alerts.findAll()).hasSize(alertsAfterFirstRun);
        // The second run still evaluates everything; it just creates nothing new.
        assertThat(second.runs()).allSatisfy(run -> assertThat(run.alertsCreated()).isZero());
        assertThat(detectionRuns.findAll()).hasSize(10); // 5 strategies x 2 runs; every run is still recorded
    }
}
