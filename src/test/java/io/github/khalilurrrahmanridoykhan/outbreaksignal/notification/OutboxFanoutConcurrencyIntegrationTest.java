package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.TestcontainersConfiguration;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.Alert;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.DetectionRun;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.DetectionRunRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.Severity;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnit;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnitRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Series;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.SeriesRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the plan's "concurrent pollers never double-claim a row": many
 * threads, each with their own database connection, repeatedly calling
 * {@link OutboxFanoutService#claimAndFanOut()} against the same seeded batch
 * of pending events — real concurrent transactions against a real
 * PostgreSQL, not a mocked lock. If {@code FOR UPDATE SKIP LOCKED} (or the
 * same-transaction status flip in {@code claimAndFanOut}) were missing, two
 * threads could claim the same row, and this test would see a claimed id
 * twice.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxFanoutConcurrencyIntegrationTest {

    private static final int EVENT_COUNT = 30;
    private static final int THREAD_COUNT = 6;

    @Autowired
    private OutboxFanoutService fanoutService;
    @Autowired
    private OutboxEventRepository outboxEvents;
    @Autowired
    private OrgUnitRepository orgUnits;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private DetectionRunRepository detectionRuns;
    @Autowired
    private AlertRepository alerts;

    private Long alertId;

    @BeforeEach
    void seedManyPendingOutboxEvents() {
        OrgUnit orgUnit = orgUnits.save(new OrgUnit("QwMiPiME3bA", "Kpanga Kabonde", 3, null));
        Series series = seriesRepository.save(new Series(orgUnit, "vq2qO3eTrNi", "IDSR Malaria"));
        DetectionRun run = detectionRuns.save(new DetectionRun("EARS_C1", "{}", 104, Instant.now()));
        Alert alert = alerts.save(new Alert(series, LocalDate.of(2026, 1, 5), "EARS_C1",
                new BigDecimal("9.5"), new BigDecimal("3.0"), Severity.HIGH, run, Instant.now()));
        alertId = alert.getId();

        for (int i = 0; i < EVENT_COUNT; i++) {
            outboxEvents.save(new OutboxEvent(alertId, AlertStatus.OPEN, "{}", Instant.now()));
        }
    }

    @AfterEach
    void cleanUp() {
        outboxEvents.deleteAll();
        alerts.deleteAll();
        detectionRuns.deleteAll();
        seriesRepository.deleteAll();
        orgUnits.deleteAll();
    }

    @Test
    void everyEventIsClaimedExactlyOnceAcrossConcurrentPollers()
            throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readySignal = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<List<Long>>> futures = new ArrayList<>();

        for (int t = 0; t < THREAD_COUNT; t++) {
            futures.add(pool.submit(() -> {
                readySignal.countDown();
                startSignal.await();
                List<Long> claimedByThisThread = new ArrayList<>();
                // Keep polling until this thread sees nothing left to claim,
                // so slower threads still get a fair share of the batch
                // rather than the first thread hoovering up everything.
                List<OutboxEvent> batch;
                do {
                    batch = fanoutService.claimAndFanOut();
                    batch.forEach(event -> claimedByThisThread.add(event.getId()));
                } while (!batch.isEmpty());
                return claimedByThisThread;
            }));
        }

        readySignal.await();
        startSignal.countDown();

        List<Long> allClaimed = new ArrayList<>();
        for (Future<List<Long>> future : futures) {
            allClaimed.addAll(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(allClaimed).hasSize(EVENT_COUNT);
        assertThat(allClaimed.stream().collect(Collectors.toSet())).hasSize(EVENT_COUNT);
        assertThat(outboxEvents.findAllById(allClaimed))
                .allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.DISPATCHED));
    }
}
