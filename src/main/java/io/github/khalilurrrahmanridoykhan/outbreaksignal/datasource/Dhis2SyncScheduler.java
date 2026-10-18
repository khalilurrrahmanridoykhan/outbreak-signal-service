package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class Dhis2SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(Dhis2SyncScheduler.class);

    private final Dhis2SyncService syncService;

    Dhis2SyncScheduler(Dhis2SyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "${outbreak.sync.cron}")
    @SchedulerLock(name = "dhis2-sync", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    void scheduledSync() {
        log.info("Starting scheduled DHIS2 sync");
        syncService.sync();
    }
}
