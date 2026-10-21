package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.Severity;
import java.util.Optional;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.junit.jupiter.api.Test;

class AlertStatusFhirMappingTest {

    @Test
    void everyAlertStatusRoundTripsThroughItsFhirStatus() {
        for (AlertStatus status : AlertStatus.values()) {
            DetectedIssue.DetectedIssueStatus fhirStatus = AlertStatusFhirMapping.toFhirStatus(status);
            assertThat(AlertStatusFhirMapping.toAlertStatus(fhirStatus.toCode()))
                    .as("round trip for %s", status)
                    .contains(status);
        }
    }

    @Test
    void theFourFhirStatusesAreAllDistinct() {
        long distinct = java.util.Arrays.stream(AlertStatus.values())
                .map(AlertStatusFhirMapping::toFhirStatus)
                .distinct()
                .count();
        assertThat(distinct).isEqualTo(AlertStatus.values().length);
    }

    @Test
    void anUnrecognisedButSyntacticallyValidStatusCodeMapsToNothing() {
        assertThat(AlertStatusFhirMapping.toAlertStatus("amended")).isEmpty();
        assertThat(AlertStatusFhirMapping.toAlertStatus("entered-in-error")).isEmpty();
        assertThat(AlertStatusFhirMapping.toAlertStatus("not-a-real-code")).isEmpty();
    }

    @Test
    void severityMapsOneToOne() {
        assertThat(AlertStatusFhirMapping.toFhirSeverity(Severity.LOW)).isEqualTo(DetectedIssue.DetectedIssueSeverity.LOW);
        assertThat(AlertStatusFhirMapping.toFhirSeverity(Severity.MEDIUM)).isEqualTo(DetectedIssue.DetectedIssueSeverity.MODERATE);
        assertThat(AlertStatusFhirMapping.toFhirSeverity(Severity.HIGH)).isEqualTo(DetectedIssue.DetectedIssueSeverity.HIGH);
    }
}
