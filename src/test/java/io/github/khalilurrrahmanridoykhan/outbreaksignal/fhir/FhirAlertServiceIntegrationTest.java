package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.TestcontainersConfiguration;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.Alert;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertService;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.DetectionRun;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.DetectionRunRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.Severity;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnit;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnitRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Series;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.SeriesRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;

/**
 * Drives four real alerts — one per {@link AlertStatus} — through the real
 * {@link AlertService} transitions, then through the real
 * {@link FhirAlertService} against a real PostgreSQL, and validates every
 * resulting {@code DetectedIssue} with {@code FhirInstanceValidator}: the
 * plan's "Done when" gate with real data, not just hand-built fixtures.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FhirAlertServiceIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);
    private static final String ANALYST = "analyst@example.org";

    private static FhirValidator validator;

    @Autowired
    private FhirAlertService fhirAlertService;
    @Autowired
    private AlertService alertService;
    @Autowired
    private AlertRepository alerts;
    @Autowired
    private DetectionRunRepository detectionRuns;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private OrgUnitRepository orgUnits;

    private final Map<AlertStatus, Long> alertIdsByStatus = new EnumMap<>(AlertStatus.class);

    @BeforeAll
    static void setUpValidator() {
        FhirContext ctx = FhirContext.forR4();
        ValidationSupportChain supportChain = new ValidationSupportChain(
                new DefaultProfileValidationSupport(ctx),
                new InMemoryTerminologyServerValidationSupport(ctx),
                new CommonCodeSystemsTerminologyService(ctx));
        validator = ctx.newValidator();
        validator.registerValidatorModule(new FhirInstanceValidator(supportChain));
    }

    @BeforeEach
    void seedOneAlertPerStatus() {
        OrgUnit orgUnit = orgUnits.save(new OrgUnit("QwMiPiME3bA", "Kpanga Kabonde", 3, null));
        Series series = seriesRepository.save(new Series(orgUnit, "vq2qO3eTrNi", "IDSR Malaria"));
        DetectionRun run = detectionRuns.save(new DetectionRun("EARS_C1", "{}", 104, Instant.now()));

        alertIdsByStatus.put(AlertStatus.OPEN, seedAlert(series, run, MONDAY).getId());
        Long toAcknowledge = seedAlert(series, run, MONDAY.plusWeeks(1)).getId();
        Long toResolve = seedAlert(series, run, MONDAY.plusWeeks(2)).getId();
        Long toDismiss = seedAlert(series, run, MONDAY.plusWeeks(3)).getId();

        alertService.acknowledge(toAcknowledge, ANALYST);
        alertIdsByStatus.put(AlertStatus.ACKNOWLEDGED, toAcknowledge);

        alertService.acknowledge(toResolve, ANALYST);
        alertService.resolve(toResolve, ANALYST);
        alertIdsByStatus.put(AlertStatus.RESOLVED, toResolve);

        alertService.dismiss(toDismiss, "Reporting delay, not a real rise", ANALYST);
        alertIdsByStatus.put(AlertStatus.DISMISSED, toDismiss);
    }

    private Alert seedAlert(Series series, DetectionRun run, LocalDate periodStart) {
        return alerts.save(new Alert(series, periodStart, "EARS_C1",
                new BigDecimal("9.5"), new BigDecimal("3.0"), Severity.HIGH, run, Instant.now()));
    }

    @AfterEach
    void cleanUp() {
        alerts.deleteAll();
        detectionRuns.deleteAll();
        seriesRepository.deleteAll();
        orgUnits.deleteAll();
    }

    @Test
    void everyRealAlertStatusMapsToAValidDetectedIssue() {
        alertIdsByStatus.forEach((status, id) -> {
            DetectedIssue issue = fhirAlertService.findById(id);
            assertThat(issue.getStatus()).isEqualTo(AlertStatusFhirMapping.toFhirStatus(status));

            ValidationResult result = validator.validateWithResult(issue);
            assertThat(result.isSuccessful())
                    .as(() -> "alert status=%s messages=%s".formatted(status, result.getMessages()))
                    .isTrue();
        });
    }

    @Test
    void searchingByStatusFindsOnlyThatAlert() {
        Bundle bundle = fhirAlertService.search("cancelled", null, Pageable.unpaged());

        assertThat(bundle.getTotal()).isEqualTo(1);
        assertThat(((DetectedIssue) bundle.getEntryFirstRep().getResource()).getId())
                .isEqualTo(String.valueOf(alertIdsByStatus.get(AlertStatus.DISMISSED)));
    }

    @Test
    void searchingByAnUnmappedStatusCodeReturnsAnEmptyBundle() {
        Bundle bundle = fhirAlertService.search("amended", null, Pageable.unpaged());

        assertThat(bundle.getTotal()).isZero();
        assertThat(bundle.getEntry()).isEmpty();
    }

    @Test
    void searchingByIdentifiedMatchesTheAlertedWeek() {
        Bundle bundle = fhirAlertService.search(null, MONDAY, Pageable.unpaged());

        assertThat(bundle.getTotal()).isEqualTo(1);
        assertThat(((DetectedIssue) bundle.getEntryFirstRep().getResource()).getId())
                .isEqualTo(String.valueOf(alertIdsByStatus.get(AlertStatus.OPEN)));
    }
}
