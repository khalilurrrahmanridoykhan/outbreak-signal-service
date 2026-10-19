package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps this package's own exceptions to RFC 9457 problem documents; see {@code common.ApiExceptionHandler} for the generic ones. */
@RestControllerAdvice
class ObservationExceptionHandler {

    @ExceptionHandler(SeriesNotFoundException.class)
    ProblemDetail handleNotFound(SeriesNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }
}
