package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Triggers a sync on demand, restricted to {@code ADMIN} — the same as the
 * scheduled trigger already is implicitly, since that one runs only inside
 * this service with no caller at all. Calling it talks to whatever DHIS2
 * instance is configured, which defaults to the public demo.
 */
@RestController
@RequestMapping("/api/v1")
class Dhis2SyncController {

    private final Dhis2SyncService syncService;

    Dhis2SyncController(Dhis2SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    SyncResult triggerSync() {
        return syncService.sync();
    }
}
