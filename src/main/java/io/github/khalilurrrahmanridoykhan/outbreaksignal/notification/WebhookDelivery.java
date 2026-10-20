package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

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
 * One (outbox event, subscription) delivery attempt. {@code outboxEventId}
 * and {@code subscriptionId} are plain columns, not JPA associations: the
 * dispatcher claims a batch, then sends every HTTP call outside any database
 * transaction (a slow or hanging endpoint must never hold a transaction
 * open), so nothing here can be a lazy association read after that
 * transaction closed — see DECISIONS.md and the Phase 4 bug this avoids by
 * construction rather than by discipline.
 */
@Entity
@Table(name = "webhook_delivery")
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_event_id", nullable = false)
    private Long outboxEventId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WebhookDeliveryStatus status = WebhookDeliveryStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(Long outboxEventId, Long subscriptionId, Instant now) {
        this.outboxEventId = outboxEventId;
        this.subscriptionId = subscriptionId;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getOutboxEventId() {
        return outboxEventId;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public WebhookDeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markSending(Instant now) {
        status = WebhookDeliveryStatus.SENDING;
        updatedAt = now;
    }

    public void markDelivered(Instant now) {
        status = WebhookDeliveryStatus.DELIVERED;
        lastError = null;
        updatedAt = now;
    }

    /** Records a failed attempt, moving to {@code DEAD_LETTERED} once {@code maxAttempts} is reached. */
    public void markFailed(String error, int maxAttempts, Instant nextAttemptAt, Instant now) {
        attempts++;
        lastError = error;
        updatedAt = now;
        status = attempts >= maxAttempts ? WebhookDeliveryStatus.DEAD_LETTERED : WebhookDeliveryStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
    }
}
