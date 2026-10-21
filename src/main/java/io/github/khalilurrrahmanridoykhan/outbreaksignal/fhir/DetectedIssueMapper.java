package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertSummary;
import java.util.Date;
import java.util.TimeZone;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

/**
 * Maps one {@link AlertSummary} — already resolved, with no lazy association
 * left to read — to a {@link DetectedIssue}. See {@code docs/fhir-mapping.md}
 * for the full field-by-field table and why {@code DetectedIssue} was chosen
 * over {@code Flag}.
 */
@Component
class DetectedIssueMapper {

    /**
     * A code system this project mints for itself: R4 has no standard
     * terminology for "which statistical aberration-detection algorithm
     * raised this", so {@code code} uses this project's own stable
     * identifiers (the same ones this API already reports as
     * {@code algorithm}) rather than force-fitting an unrelated standard
     * terminology.
     */
    static final String ALGORITHM_CODE_SYSTEM =
            "https://github.com/khalilurrrahmanridoykhan/outbreak-signal-service/fhir/CodeSystem/detection-algorithm";

    /**
     * {@code implicated} expects references to resources such as
     * {@code Medication} or {@code Procedure} that caused a clinical issue —
     * this project has no FHIR resource modelling an organisation unit, so a
     * reference there would either be fabricated or simply wrong. A
     * self-defined extension carries the same information honestly instead.
     * See {@code docs/fhir-mapping.md}.
     */
    static final String ORG_UNIT_EXTENSION_URL =
            "https://github.com/khalilurrrahmanridoykhan/outbreak-signal-service/fhir/StructureDefinition/org-unit";

    DetectedIssue map(AlertSummary alert) {
        DetectedIssue issue = new DetectedIssue();
        issue.setId(String.valueOf(alert.id()));
        issue.setStatus(AlertStatusFhirMapping.toFhirStatus(alert.status()));
        issue.setSeverity(AlertStatusFhirMapping.toFhirSeverity(alert.severity()));
        issue.setCode(algorithmCode(alert.algorithm()));
        issue.setIdentified(identifiedDateTime(alert));
        issue.setDetail(detailText(alert));
        issue.addExtension(orgUnitExtension(alert));
        return issue;
    }

    private CodeableConcept algorithmCode(String algorithm) {
        return new CodeableConcept().addCoding(new Coding()
                .setSystem(ALGORITHM_CODE_SYSTEM)
                .setCode(algorithm)
                .setDisplay(algorithm.replace('_', ' ')));
    }

    private DateTimeType identifiedDateTime(AlertSummary alert) {
        DateTimeType dateTime = new DateTimeType(Date.from(alert.createdAt()));
        dateTime.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateTime;
    }

    private String detailText(AlertSummary alert) {
        return "%s detected a score of %s against a threshold of %s for %s in %s (week of %s)."
                .formatted(alert.algorithm(), alert.score().toPlainString(), alert.threshold().toPlainString(),
                        alert.dataElementName(), alert.orgUnitName(), alert.periodStart());
    }

    private Extension orgUnitExtension(AlertSummary alert) {
        Extension extension = new Extension(ORG_UNIT_EXTENSION_URL);
        extension.addExtension("id", new StringType(String.valueOf(alert.orgUnitId())));
        extension.addExtension("name", new StringType(alert.orgUnitName()));
        return extension;
    }
}
