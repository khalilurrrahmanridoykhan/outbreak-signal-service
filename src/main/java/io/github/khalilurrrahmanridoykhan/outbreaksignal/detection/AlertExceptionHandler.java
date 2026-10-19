package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps this package's own exceptions to RFC 9457 problem documents; see {@code ApiExceptionHandler} for the generic ones. */
@RestControllerAdvice
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
