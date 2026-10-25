package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * See {@code detection.AlertExceptionHandler} for why this needs
 * {@code @Order(HIGHEST_PRECEDENCE)}: without it, the generic
 * {@code ApiExceptionHandler}'s catch-all can shadow this package's own,
 * more specific handler. Scoped to this package's own controllers for the
 * same reason every package-specific advice now is: see that same javadoc.
 */
@RestControllerAdvice(basePackages = "io.github.khalilurrrahmanridoykhan.outbreaksignal.notification")
@Order(Ordered.HIGHEST_PRECEDENCE)
class NotificationExceptionHandler {

    @ExceptionHandler(WebhookSubscriptionNotFoundException.class)
    ProblemDetail handleNotFound(WebhookSubscriptionNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(NotificationRecipientNotFoundException.class)
    ProblemDetail handleNotFound(NotificationRecipientNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }
}
