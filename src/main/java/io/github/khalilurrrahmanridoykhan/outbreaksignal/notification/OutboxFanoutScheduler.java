package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OutboxFanoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxFanoutScheduler.class);

    private final OutboxFanoutService fanoutService;
    private final AlertEmailNotifier emailNotifier;

    OutboxFanoutScheduler(OutboxFanoutService fanoutService, AlertEmailNotifier emailNotifier) {
        this.fanoutService = fanoutService;
        this.emailNotifier = emailNotifier;
    }

    @Scheduled(fixedDelayString = "${outbreak.notification.outbox.poll-interval-ms}")
    @SchedulerLock(name = "outbox-fanout", lockAtLeastFor = "PT1S", lockAtMostFor = "PT5M")
    void poll() {
        fanOutAndNotify();
    }

    /**
     * Kept separate from {@link #poll()} so tests can call it directly —
     * see {@code WebhookDispatchScheduler.dispatchDueDeliveries} for why a
     * {@code @SchedulerLock}-guarded method cannot just be called again a
     * moment later in a test and expected to run.
     */
    void fanOutAndNotify() {
        List<OutboxEvent> claimed = fanoutService.claimAndFanOut();
        for (OutboxEvent event : claimed) {
            try {
                emailNotifier.notifyIfEnabled(event);
            } catch (RuntimeException e) {
                // One event's email failing must never stop the rest of the
                // batch from being emailed and dispatched to webhooks — the
                // same per-item isolation Dhis2SyncService already relies on.
                log.warn("Email notification failed for outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
        if (!claimed.isEmpty()) {
            log.info("Fanned out {} outbox event(s)", claimed.size());
        }
    }
}
