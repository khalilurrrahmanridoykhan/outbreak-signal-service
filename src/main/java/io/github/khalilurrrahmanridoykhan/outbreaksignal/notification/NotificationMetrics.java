package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Two gauges, re-read on every scrape rather than pushed: how far behind the
 * outbox fan-out poller is (age of its oldest still-pending event), and how
 * large the webhook dispatcher's backlog is (deliveries still {@code PENDING}).
 * Both are zero in the steady state and only grow when a poller stalls —
 * exactly the plan's "outbox lag" and "dispatch failures" observability ask.
 */
@Component
class NotificationMetrics {

    NotificationMetrics(MeterRegistry registry, OutboxEventRepository outboxEvents,
            WebhookDeliveryRepository deliveries, Clock clock) {
        Gauge.builder("outbreak.outbox.lag.seconds", outboxEvents, repo -> lagSeconds(repo, clock))
                .description("Age in seconds of the oldest still-pending outbox event; 0 when nothing is pending")
                .register(registry);
        Gauge.builder("outbreak.webhook.deliveries.pending", deliveries,
                        repo -> (double) repo.countByStatus(WebhookDeliveryStatus.PENDING))
                .description("Webhook deliveries waiting to be sent or retried")
                .register(registry);
    }

    private static double lagSeconds(OutboxEventRepository outboxEvents, Clock clock) {
        Instant oldestPending = outboxEvents.findOldestPendingCreatedAt();
        return oldestPending == null ? 0.0 : Duration.between(oldestPending, clock.instant()).getSeconds();
    }
}
