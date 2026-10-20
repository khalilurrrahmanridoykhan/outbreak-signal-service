package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

/**
 * The body of {@code POST /webhook-subscriptions}. {@code secret} is
 * optional — leave it out to have the server generate one, returned once in
 * the response. {@code eventTypes} is optional too; omit it (or send an
 * empty set) to receive every event type.
 */
public record CreateWebhookSubscriptionRequest(@NotBlank String url, String secret, Set<AlertStatus> eventTypes) {
}
