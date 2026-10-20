package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims a batch of pending {@link OutboxEvent} rows and fans each one out
 * to a {@link WebhookDelivery} row per currently-active, matching
 * {@link WebhookSubscription} — all in one transaction, which is what makes
 * the {@code FOR UPDATE SKIP LOCKED} claim in {@link OutboxEventRepository}
 * actually exclusive: the claimed rows are marked
 * {@link OutboxEventStatus#DISPATCHED} before this transaction commits, so a
 * second poller racing this one (another instance, or another thread in a
 * test) never sees them as claimable, whether or not it would have been
 * blocked by the row lock itself.
 */
@Service
class OutboxFanoutService {

    private final OutboxEventRepository outboxEvents;
    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final OutbreakProperties properties;
    private final Clock clock;

    OutboxFanoutService(
            OutboxEventRepository outboxEvents,
            WebhookSubscriptionRepository subscriptions,
            WebhookDeliveryRepository deliveries,
            OutbreakProperties properties,
            Clock clock) {
        this.outboxEvents = outboxEvents;
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    List<OutboxEvent> claimAndFanOut() {
        int batchSize = properties.notification().outbox().batchSize();
        List<Long> claimableIds = outboxEvents.findClaimableIds(batchSize);
        if (claimableIds.isEmpty()) {
            return List.of();
        }

        List<OutboxEvent> claimed = outboxEvents.findAllById(claimableIds);
        List<WebhookSubscription> activeSubscriptions = subscriptions.findByActiveTrue();
        Instant now = clock.instant();
        for (OutboxEvent event : claimed) {
            for (WebhookSubscription subscription : activeSubscriptions) {
                if (subscription.wants(event.getEventType())) {
                    deliveries.save(new WebhookDelivery(event.getId(), subscription.getId(), now));
                }
            }
        }
        outboxEvents.markDispatched(claimableIds, now);
        return claimed;
    }
}
