package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

/** No series exists with the requested id. Mapped to 404 by {@code ObservationExceptionHandler}. */
public class SeriesNotFoundException extends RuntimeException {

    public SeriesNotFoundException(Long id) {
        super("No series with id " + id);
    }
}
