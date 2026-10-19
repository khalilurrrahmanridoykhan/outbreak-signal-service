package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.DetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * No other test exercises {@link Dhis2SyncScheduler} itself — the
 * {@code sync()} and {@code runDetection()} tests each cover their own
 * service directly. This is what actually proves the plan's "detection runs
 * after each successful sync" is wired the way the code comments claim.
 *
 * <p>The manual {@code POST /api/v1/sync} trigger deliberately calls only
 * {@code Dhis2SyncService.sync()}, not detection — Phase 4 adds its own
 * separate manual {@code POST /detection/run}, so today's manual sync
 * trigger intentionally does not also run detection. Only the scheduled
 * path chains the two.
 */
@ExtendWith(MockitoExtension.class)
class Dhis2SyncSchedulerTest {

    @Mock
    private Dhis2SyncService syncService;
    @Mock
    private DetectionService detectionService;

    @Test
    void scheduledSyncRunsSyncThenDetectionInThatOrder() {
        Dhis2SyncScheduler scheduler = new Dhis2SyncScheduler(syncService, detectionService);

        scheduler.scheduledSync();

        InOrder order = Mockito.inOrder(syncService, detectionService);
        order.verify(syncService).sync();
        order.verify(detectionService).runDetection();
        verifyNoMoreInteractions(syncService, detectionService);
    }
}
