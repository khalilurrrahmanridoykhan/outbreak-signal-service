package io.github.khalilurrrahmanridoykhan.outbreaksignal.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into an RFC 9457 problem document. Framework exceptions
 * (validation, unreadable body, unsupported media type, ...) are handled by the
 * parent class; the handlers below cover what it does not. Only generic,
 * domain-independent failures live here — a package with its own exceptions
 * (such as {@code detection}'s {@code AlertExceptionHandler}) owns mapping
 * those itself, so this class never needs to depend on any domain package.
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)} is deliberate: Spring resolves
 * {@code @ControllerAdvice} beans in order and stops at the first one with
 * any applicable handler, rather than finding the single most specific match
 * across every advice bean in the application. Without this, the broad
 * {@code Exception.class} handler below could shadow a more specific one
 * declared in a different advice class (such as {@code AlertNotFoundException}'s
 * 404 in {@code AlertExceptionHandler}) whenever this bean happened to be
 * checked first — found by {@code AlertControllerTest} returning 500 for
 * what should have been 404s and 409s.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
        problem.setTitle("Forbidden");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The request conflicts with data that already exists.");
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleStaleUpdate(ObjectOptimisticLockingFailureException ex) {
        log.info("Optimistic locking failure: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "This record was changed by someone else; reload it and try again.");
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadInput(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Internal Server Error");
        return problem;
    }
}
