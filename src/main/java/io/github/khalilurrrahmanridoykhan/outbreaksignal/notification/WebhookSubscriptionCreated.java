package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import java.time.Instant;
import java.util.Set;

/**
 * The one response that ever carries the full {@code secret} — whether it
 * was supplied in the request or generated. Store it now; it cannot be
 * retrieved again.
 */
public record WebhookSubscriptionCreated(
        Long id, String url, String secret, Set<AlertStatus> eventTypes, boolean active, Instant createdAt) {
}
