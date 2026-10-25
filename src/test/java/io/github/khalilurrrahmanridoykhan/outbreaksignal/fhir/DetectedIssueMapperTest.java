package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertSummary;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.hl7.fhir.r4.model.Extension;
import org.junit.jupiter.api.Test;

class DetectedIssueMapperTest {

    private final DetectedIssueMapper mapper = new DetectedIssueMapper();

    private static AlertSummary sampleAlert(AlertStatus status) {
        return new AlertSummary(
                62L, 100L, 7L, "Kpanga Kabonde", "IDSR Malaria", "EARS_C3",
                LocalDate.of(2026, 1, 5), new BigDecimal("9.50"), new BigDecimal("3.00"),
                Severity.HIGH, status, 0L, Instant.parse("2026-01-12T03:00:00Z"));
    }

    @Test
    void mapsTheIdStatusSeverityAndCode() {
        DetectedIssue issue = mapper.map(sampleAlert(AlertStatus.ACKNOWLEDGED));

        assertThat(issue.getId()).isEqualTo("62");
        assertThat(issue.getStatus()).isEqualTo(DetectedIssue.DetectedIssueStatus.PRELIMINARY);
        assertThat(issue.getSeverity()).isEqualTo(DetectedIssue.DetectedIssueSeverity.HIGH);
        assertThat(issue.getCode().getCodingFirstRep().getSystem()).isEqualTo(DetectedIssueMapper.ALGORITHM_CODE_SYSTEM);
        assertThat(issue.getCode().getCodingFirstRep().getCode()).isEqualTo("EARS_C3");
    }

    @Test
    void identifiedIsTheAlertsCreatedAtInstant() {
        DetectedIssue issue = mapper.map(sampleAlert(AlertStatus.OPEN));

        assertThat(issue.getIdentifiedDateTimeType().getValue().toInstant()).isEqualTo(Instant.parse("2026-01-12T03:00:00Z"));
    }

    @Test
    void detailNamesTheAlgorithmScoreThresholdAndWhereAndWhen() {
        DetectedIssue issue = mapper.map(sampleAlert(AlertStatus.OPEN));

        assertThat(issue.getDetail())
                .contains("EARS_C3")
                .contains("9.5")
                .contains("3")
                .contains("IDSR Malaria")
                .contains("Kpanga Kabonde")
                .contains("2026-01-05");
    }

    @Test
    void theOrgUnitExtensionCarriesIdAndName() {
        DetectedIssue issue = mapper.map(sampleAlert(AlertStatus.OPEN));

        Extension orgUnit = issue.getExtensionByUrl(DetectedIssueMapper.ORG_UNIT_EXTENSION_URL);
        assertThat(orgUnit).isNotNull();
        assertThat(orgUnit.getExtensionByUrl("id").getValue().primitiveValue()).isEqualTo("7");
        assertThat(orgUnit.getExtensionByUrl("name").getValue().primitiveValue()).isEqualTo("Kpanga Kabonde");
    }

    @Test
    void everyAlertStatusMapsWithoutError() {
        for (AlertStatus status : AlertStatus.values()) {
            DetectedIssue issue = mapper.map(sampleAlert(status));
            assertThat(issue.getStatus()).isNotEqualTo(DetectedIssue.DetectedIssueStatus.NULL);
        }
    }
}
