package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

/** Whether an {@link OutboxEvent} has been fanned out to its deliveries yet. */
public enum OutboxEventStatus {
    PENDING,
    DISPATCHED
}
