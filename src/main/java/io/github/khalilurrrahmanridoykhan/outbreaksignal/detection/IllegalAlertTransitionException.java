package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

/** An alert cannot move from its current status to the one requested. Mapped to 409 by {@code ApiExceptionHandler}. */
public class IllegalAlertTransitionException extends RuntimeException {

    private final AlertStatus from;
    private final AlertStatus to;

    public IllegalAlertTransitionException(AlertStatus from, AlertStatus to) {
        super("Cannot move an alert from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public AlertStatus from() {
        return from;
    }

    public AlertStatus to() {
        return to;
    }
}
