package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.time.Instant;

/** One entry of an alert's reviewed history, as {@code GET /alerts/{id}} returns it. */
public record AlertEventSummary(AlertStatus fromStatus, AlertStatus toStatus, String note, String actor, Instant createdAt) {

    static AlertEventSummary from(AlertEvent event) {
        return new AlertEventSummary(event.getFromStatus(), event.getToStatus(), event.getNote(), event.getActor(), event.getCreatedAt());
    }
}
