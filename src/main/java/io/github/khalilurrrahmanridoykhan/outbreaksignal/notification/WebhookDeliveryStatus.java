package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

/**
 * One {@link WebhookDelivery}'s lifecycle: {@code PENDING} (not yet sent, or
 * due for a retry) → {@code SENDING} (claimed by a dispatcher, HTTP call in
 * flight) → either back to {@code PENDING} with a later {@code nextAttemptAt}
 * on failure, {@code DELIVERED} on a 2xx response, or {@code DEAD_LETTERED}
 * once the configured attempt limit is reached.
 */
public enum WebhookDeliveryStatus {
    PENDING,
    SENDING,
    DELIVERED,
    DEAD_LETTERED
}
