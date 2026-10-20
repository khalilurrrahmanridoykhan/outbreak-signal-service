package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import java.time.Instant;

/** {@code GET /webhook-deliveries}: one delivery attempt record, including dead-lettered ones. */
public record WebhookDeliverySummary(
        Long id,
        Long outboxEventId,
        Long subscriptionId,
        WebhookDeliveryStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {

    static WebhookDeliverySummary from(WebhookDelivery delivery) {
        return new WebhookDeliverySummary(
                delivery.getId(), delivery.getOutboxEventId(), delivery.getSubscriptionId(), delivery.getStatus(),
                delivery.getAttempts(), delivery.getNextAttemptAt(), delivery.getLastError(),
                delivery.getCreatedAt(), delivery.getUpdatedAt());
    }
}
