package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this package's own exceptions to RFC 9457 problem documents; see
 * {@code ApiExceptionHandler} for the generic ones and for why this needs
 * {@code @Order(HIGHEST_PRECEDENCE)} — without it, an unrelated
 * {@code @ControllerAdvice} with a broad catch-all handler can shadow this
 * one's more specific handlers entirely, regardless of how specific they are.
 *
 * <p>Scoped to this package's own controllers ({@code basePackages}): the
 * {@code fhir} package reuses {@link AlertNotFoundException} but needs a
 * {@code 404} that is itself a valid FHIR resource ({@code OperationOutcome},
 * not {@code ProblemDetail}) — see {@code fhir.FhirExceptionHandler}. Without
 * this restriction, two {@code HIGHEST_PRECEDENCE} advices would both be
 * "applicable" to a {@code fhir} controller throwing that exception, and
 * which one actually won would depend on registration order rather than
 * being deterministic.
 */
@RestControllerAdvice(basePackages = "io.github.khalilurrrahmanridoykhan.outbreaksignal.detection")
@Order(Ordered.HIGHEST_PRECEDENCE)
class AlertExceptionHandler {

    @ExceptionHandler(AlertNotFoundException.class)
    ProblemDetail handleNotFound(AlertNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(IllegalAlertTransitionException.class)
    ProblemDetail handleIllegalTransition(IllegalAlertTransitionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        problem.setProperty("from", ex.from());
        problem.setProperty("to", ex.to());
        return problem;
    }
}
