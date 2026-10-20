package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims {@link WebhookDelivery} rows and resolves everything one HTTP
 * attempt needs into a plain {@link WebhookDispatchJob}, all inside one
 * transaction — the actual send happens outside it, in
 * {@code WebhookDispatchScheduler}, since a transaction must never stay open
 * across a network call to an endpoint this service does not control.
 */
@Service
class WebhookDispatchService {

    private final WebhookDeliveryRepository deliveries;
    private final OutboxEventRepository outboxEvents;
    private final WebhookSubscriptionRepository subscriptions;
    private final OutbreakProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    WebhookDispatchService(
            WebhookDeliveryRepository deliveries,
            OutboxEventRepository outboxEvents,
            WebhookSubscriptionRepository subscriptions,
            OutbreakProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.deliveries = deliveries;
        this.outboxEvents = outboxEvents;
        this.subscriptions = subscriptions;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    List<WebhookDispatchJob> claimBatch() {
        OutbreakProperties.Notification.Webhook config = properties.notification().webhook();
        Instant now = clock.instant();
        Instant staleBefore = now.minusMillis(config.staleSendingTimeoutMs());
        List<Long> claimableIds = deliveries.findClaimableIds(now, staleBefore, config.batchSize());
        if (claimableIds.isEmpty()) {
            return List.of();
        }
        deliveries.markSending(claimableIds, now);

        List<WebhookDispatchJob> jobs = new ArrayList<>(claimableIds.size());
        for (WebhookDelivery delivery : deliveries.findAllById(claimableIds)) {
            OutboxEvent event = outboxEvents.findById(delivery.getOutboxEventId()).orElseThrow();
            WebhookSubscription subscription = subscriptions.findById(delivery.getSubscriptionId()).orElseThrow();
            jobs.add(new WebhookDispatchJob(
                    delivery.getId(), subscription.getUrl(), subscription.getSecret(),
                    event.getId(), buildEnvelope(event), delivery.getAttempts()));
        }
        return jobs;
    }

    @Transactional
    void markDelivered(Long deliveryId) {
        deliveries.findById(deliveryId).ifPresent(delivery -> delivery.markDelivered(clock.instant()));
    }

    @Transactional
    void markFailed(Long deliveryId, String error) {
        OutbreakProperties.Notification.Webhook config = properties.notification().webhook();
        deliveries.findById(deliveryId).ifPresent(delivery -> {
            Instant now = clock.instant();
            long backoffMs = (long) (config.initialBackoffMs()
                    * Math.pow(config.backoffMultiplier(), delivery.getAttempts()));
            delivery.markFailed(error, config.maxAttempts(), now.plusMillis(backoffMs), now);
        });
    }

    private String buildEnvelope(OutboxEvent event) {
        JsonNode alert = objectMapper.readTree(event.getPayload());
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", event.getId());
        envelope.put("eventType", event.getEventType().name());
        envelope.put("occurredAt", event.getCreatedAt().toString());
        envelope.set("alert", alert);
        return objectMapper.writeValueAsString(envelope);
    }
}
