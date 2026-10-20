package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Sends every claimed delivery's HTTP POST, signed and timestamped per
 * {@link WebhookSigner}. Delivery is at-least-once: a crash between a
 * successful POST and committing {@code DELIVERED} leaves the row claimable
 * again on the next poll (once its stale-{@code SENDING} window passes), so
 * the same event id can reach a receiver twice — the receiver's own
 * deduplication on {@code X-Event-Id} is what makes that safe, documented in
 * the README.
 */
@Component
class WebhookDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchScheduler.class);

    private final WebhookDispatchService dispatchService;
    private final WebClient webhookHttpClient;
    private final WebhookSigner signer;
    private final Clock clock;

    WebhookDispatchScheduler(WebhookDispatchService dispatchService, WebClient webhookHttpClient, WebhookSigner signer, Clock clock) {
        this.dispatchService = dispatchService;
        this.webhookHttpClient = webhookHttpClient;
        this.signer = signer;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${outbreak.notification.webhook.poll-interval-ms}")
    @SchedulerLock(name = "webhook-dispatch", lockAtLeastFor = "PT1S", lockAtMostFor = "PT5M")
    void poll() {
        dispatchDueDeliveries();
    }

    /**
     * The actual claim-and-send logic, kept separate from {@link #poll()} so
     * tests can call it directly: {@code @SchedulerLock}'s
     * {@code lockAtLeastFor} holds its lock for a real wall-clock second
     * after acquisition regardless of this project's own (fake, test-only)
     * {@link Clock}, so a test calling the locked method more than once in
     * quick succession would silently no-op on every call after the first.
     */
    void dispatchDueDeliveries() {
        List<WebhookDispatchJob> jobs = dispatchService.claimBatch();
        for (WebhookDispatchJob job : jobs) {
            send(job);
        }
    }

    private void send(WebhookDispatchJob job) {
        Instant now = clock.instant();
        String signature = signer.sign(job.secret(), now, job.body());
        try {
            webhookHttpClient.post()
                    .uri(job.url())
                    .header("X-Event-Id", String.valueOf(job.eventId()))
                    .header("X-Signature", signature)
                    .header("X-Signature-Timestamp", String.valueOf(now.getEpochSecond()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(job.body())
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            dispatchService.markDelivered(job.deliveryId());
        } catch (WebClientResponseException e) {
            log.warn("Webhook delivery {} to {} failed with {}", job.deliveryId(), job.url(), e.getStatusCode());
            dispatchService.markFailed(job.deliveryId(), "HTTP " + e.getStatusCode());
        } catch (WebClientRequestException e) {
            log.warn("Webhook delivery {} to {} could not connect: {}", job.deliveryId(), job.url(), e.getMessage());
            dispatchService.markFailed(job.deliveryId(), e.getMessage());
        }
    }
}
