package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertNotFoundException;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A FHIR client expects a FHIR resource on every response, failures
 * included — {@code ProblemDetail} (what {@code ApiExceptionHandler} and
 * every other package's advice return) is not one. Scoped to this package's
 * own controllers; see {@code detection.AlertExceptionHandler}'s javadoc for
 * why that scoping is what makes two {@code HIGHEST_PRECEDENCE} advices
 * (this one and that one, both reusing {@link AlertNotFoundException})
 * resolve deterministically instead of by registration order.
 */
@RestControllerAdvice(basePackages = "io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir")
@Order(Ordered.HIGHEST_PRECEDENCE)
class FhirExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FhirExceptionHandler.class);

    @ExceptionHandler(AlertNotFoundException.class)
    ResponseEntity<OperationOutcome> handleNotFound(AlertNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(outcome(IssueType.NOTFOUND, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<OperationOutcome> handleUnexpected(Exception ex) {
        log.error("Unhandled exception in the FHIR API", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(outcome(IssueType.EXCEPTION, "An unexpected error occurred."));
    }

    private OperationOutcome outcome(IssueType type, String diagnostics) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(IssueSeverity.ERROR).setCode(type).setDiagnostics(diagnostics);
        return outcome;
    }
}
