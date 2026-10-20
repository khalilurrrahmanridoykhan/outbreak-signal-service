package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import tools.jackson.databind.ObjectMapper;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one {@link OutboxEvent} row per alert status change. Called from
 * {@code AlertService} and {@code DetectionService}, both already inside
 * their own {@code @Transactional} method when they call this — propagation
 * defaults to {@code REQUIRED}, so this join the caller's transaction rather
 * than opening a new one, which is what makes the write transactional with
 * the alert change itself: a rollback of the caller undoes this row too, and
 * nothing is ever written for an alert change that did not commit.
 */
@Service
public class AlertEventPublisher {

    private final OutboxEventRepository outboxEvents;
    private final ObjectMapper objectMapper;

    AlertEventPublisher(OutboxEventRepository outboxEvents, ObjectMapper objectMapper) {
        this.outboxEvents = outboxEvents;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(AlertEventPayload payload, Instant occurredAt) {
        outboxEvents.save(new OutboxEvent(payload.alertId(), payload.status(), toJson(payload), occurredAt));
    }

    private String toJson(AlertEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // Every field is a plain id, string, enum, number or date — this
            // can only fail if that ever changes to something Jackson cannot
            // serialise, which is a programming error worth failing loudly
            // on, not swallowing (same reasoning as DetectionService.toJson).
            throw new IllegalStateException("Could not serialise an alert event payload for " + payload, e);
        }
    }
}
