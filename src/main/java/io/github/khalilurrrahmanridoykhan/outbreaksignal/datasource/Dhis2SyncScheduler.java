package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.common.CorrelationIdFilter;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.DetectionService;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs detection as the second step of the same scheduled job, under the
 * same lock, right after a sync — per the plan, detection follows every
 * sync rather than being scheduled on its own. {@link Dhis2SyncService#sync()}
 * never throws (it isolates each data element's own failures; see
 * {@code DECISIONS.md} #17), so detection always runs once sync has at least
 * attempted every data element.
 */
@Component
class Dhis2SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(Dhis2SyncScheduler.class);

    private final Dhis2SyncService syncService;
    private final DetectionService detectionService;

    Dhis2SyncScheduler(Dhis2SyncService syncService, DetectionService detectionService) {
        this.syncService = syncService;
        this.detectionService = detectionService;
    }

    @Scheduled(cron = "${outbreak.sync.cron}")
    @SchedulerLock(name = "dhis2-sync", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    void scheduledSync() {
        MDC.put(CorrelationIdFilter.MDC_KEY, UUID.randomUUID().toString());
        try {
            log.info("Starting scheduled DHIS2 sync");
            syncService.sync();
            log.info("Starting detection after sync");
            detectionService.runDetection();
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
