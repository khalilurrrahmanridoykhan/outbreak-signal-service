package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import java.time.Instant;
import java.util.Set;

/** {@code GET /webhook-subscriptions}: never carries the secret — only the create response does, once. */
public record WebhookSubscriptionSummary(Long id, String url, Set<AlertStatus> eventTypes, boolean active, Instant createdAt) {

    static WebhookSubscriptionSummary from(WebhookSubscription subscription) {
        return new WebhookSubscriptionSummary(
                subscription.getId(), subscription.getUrl(), subscription.getEventTypes(),
                subscription.isActive(), subscription.getCreatedAt());
    }
}
