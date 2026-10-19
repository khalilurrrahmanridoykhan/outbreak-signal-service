package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

/** No alert exists with the requested id. Mapped to 404 by {@code ApiExceptionHandler}. */
public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(Long id) {
        super("No alert with id " + id);
    }
}
