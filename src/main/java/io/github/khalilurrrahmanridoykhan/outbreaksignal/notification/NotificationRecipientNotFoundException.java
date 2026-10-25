package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

/** No notification recipient exists with the requested id. Mapped to 404 by {@code NotificationExceptionHandler}. */
public class NotificationRecipientNotFoundException extends RuntimeException {

    public NotificationRecipientNotFoundException(Long id) {
        super("No notification recipient with id " + id);
    }
}
