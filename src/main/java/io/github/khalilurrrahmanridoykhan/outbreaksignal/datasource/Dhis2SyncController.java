package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Triggers a sync on demand. Unsecured for now — see DECISIONS.md #8, Spring
 * Security arrives with Phase 4's endpoints, which will restrict this one to
 * {@code ADMIN} the same as the scheduled trigger already is implicitly (it
 * runs only inside this service). Calling it talks to whatever DHIS2 instance
 * is configured, which defaults to the public demo.
 */
@RestController
@RequestMapping("/api/v1")
class Dhis2SyncController {

    private final Dhis2SyncService syncService;

    Dhis2SyncController(Dhis2SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    SyncResult triggerSync() {
        return syncService.sync();
    }
}
