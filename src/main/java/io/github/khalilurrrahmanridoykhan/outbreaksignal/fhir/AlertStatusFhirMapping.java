package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.Severity;
import java.util.Optional;
import org.hl7.fhir.r4.model.DetectedIssue;

/**
 * A deliberately **bijective** mapping to {@code DetectedIssue.status}, even
 * though FHIR's own "preliminary"/"final" reads as if {@code OPEN} and
 * {@code ACKNOWLEDGED} should share one code. Four distinct {@code AlertStatus}
 * values map to four distinct, still-sensible codes from the same R4 value
 * set instead:
 *
 * <pre>
 * OPEN         -&gt; registered   (exists; nothing has been done with it yet)
 * ACKNOWLEDGED -&gt; preliminary  (a person has started, non-final, review)
 * RESOLVED     -&gt; final        (review concluded; the signal stands)
 * DISMISSED    -&gt; cancelled    (review concluded; it was a false alarm)
 * </pre>
 *
 * Being bijective is what lets {@link #toAlertStatus} round-trip a FHIR
 * {@code status} search parameter back to exactly one {@code AlertStatus}
 * and reuse the same indexed, paginated database query the REST API already
 * has — a one-to-many mapping would have forced either an in-memory filter
 * after fetching everything, or a second query shape just for FHIR search.
 * See {@code docs/fhir-mapping.md}.
 */
final class AlertStatusFhirMapping {

    private AlertStatusFhirMapping() {
    }

    static DetectedIssue.DetectedIssueStatus toFhirStatus(AlertStatus status) {
        return switch (status) {
            case OPEN -> DetectedIssue.DetectedIssueStatus.REGISTERED;
            case ACKNOWLEDGED -> DetectedIssue.DetectedIssueStatus.PRELIMINARY;
            case RESOLVED -> DetectedIssue.DetectedIssueStatus.FINAL;
            case DISMISSED -> DetectedIssue.DetectedIssueStatus.CANCELLED;
        };
    }

    /**
     * @return the one {@code AlertStatus} that maps to {@code fhirStatusCode}, or
     *     empty if it is not one of the four codes this mapping uses — a
     *     syntactically valid {@code DetectedIssueStatus} code such as
     *     {@code "amended"} is simply a search no alert can ever match, not
     *     an error.
     */
    static Optional<AlertStatus> toAlertStatus(String fhirStatusCode) {
        return switch (fhirStatusCode) {
            case "registered" -> Optional.of(AlertStatus.OPEN);
            case "preliminary" -> Optional.of(AlertStatus.ACKNOWLEDGED);
            case "final" -> Optional.of(AlertStatus.RESOLVED);
            case "cancelled" -> Optional.of(AlertStatus.DISMISSED);
            default -> Optional.empty();
        };
    }

    static DetectedIssue.DetectedIssueSeverity toFhirSeverity(Severity severity) {
        return switch (severity) {
            case LOW -> DetectedIssue.DetectedIssueSeverity.LOW;
            case MEDIUM -> DetectedIssue.DetectedIssueSeverity.MODERATE;
            case HIGH -> DetectedIssue.DetectedIssueSeverity.HIGH;
        };
    }
}
