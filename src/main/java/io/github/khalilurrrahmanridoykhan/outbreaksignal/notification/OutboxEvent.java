package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One domain event — an alert opened, or moved to a new reviewed status —
 * written in the same transaction as that alert change (see
 * {@code AlertEventPublisher}). {@code payload} is a JSON snapshot of the
 * alert built in that same transaction, before the lazy {@code Series}/
 * {@code OrgUnit} it reads could ever go stale outside it (the same bug this
 * project already hit once in Phase 4 — see DECISIONS.md). {@code status}
 * tracks only whether fan-out to {@link WebhookDelivery} rows has happened,
 * not whether every delivery succeeded; each delivery tracks its own outcome.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private AlertStatus eventType;

    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(Long alertId, AlertStatus eventType, String payload, Instant createdAt) {
        this.alertId = alertId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getAlertId() {
        return alertId;
    }

    public AlertStatus getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }
}
