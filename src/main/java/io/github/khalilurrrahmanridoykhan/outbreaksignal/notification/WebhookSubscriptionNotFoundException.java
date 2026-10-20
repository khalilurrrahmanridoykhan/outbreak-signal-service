package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

/** No webhook subscription exists with the requested id. Mapped to 404 by {@code NotificationExceptionHandler}. */
public class WebhookSubscriptionNotFoundException extends RuntimeException {

    public WebhookSubscriptionNotFoundException(Long id) {
        super("No webhook subscription with id " + id);
    }
}
