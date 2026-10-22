package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
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
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Exercises fan-out and dispatch together against a real WireMock endpoint:
 * a signed, correctly-headed delivery on success, and retry-with-backoff
 * then dead-lettering on a consistently failing endpoint — the plan's own
 * "Done when" gate (an alert commit is delivered, exactly once from the
 * receiver's point of view via its event id, even though delivery is
 * at-least-once) and its "retry then dead-letter" test requirement.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, WebhookDispatchIntegrationTest.MutableClockConfig.class})
class WebhookDispatchIntegrationTest {

    private static final String SECRET = "a-shared-webhook-secret";

    private static WireMockServer wireMock;

    @DynamicPropertySource
    static void webhookProperties(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(0);
        wireMock.start();
        registry.add("outbreak.notification.webhook.max-attempts", () -> "3");
        registry.add("outbreak.notification.webhook.initial-backoff-ms", () -> "1000");
        registry.add("outbreak.notification.webhook.backoff-multiplier", () -> "1.0");
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    private OutboxEventRepository outboxEvents;
    @Autowired
    private WebhookSubscriptionRepository subscriptions;
    @Autowired
    private WebhookDeliveryRepository deliveries;
    @Autowired
    private OutboxFanoutService fanoutService;
    @Autowired
    private WebhookDispatchScheduler dispatchScheduler;
    @Autowired
    private WebhookSigner signer;
    @Autowired
    private MutableClock clock;
    @Autowired
    private OrgUnitRepository orgUnits;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private DetectionRunRepository detectionRuns;
    @Autowired
    private AlertRepository alerts;
    @Autowired
    private MeterRegistry meterRegistry;

    private Long alertId;

    @BeforeEach
    void seedAnAlertAndResetWireMock() {
        wireMock.resetAll();
        OrgUnit orgUnit = orgUnits.save(new OrgUnit("QwMiPiME3bA", "Kpanga Kabonde", 3, null));
        Series series = seriesRepository.save(new Series(orgUnit, "vq2qO3eTrNi", "IDSR Malaria"));
        DetectionRun run = detectionRuns.save(new DetectionRun("EARS_C1", "{}", 104, Instant.now()));
        Alert alert = alerts.save(new Alert(series, LocalDate.of(2026, 1, 5), "EARS_C1",
                new BigDecimal("9.5"), new BigDecimal("3.0"), Severity.HIGH, run, Instant.now()));
        alertId = alert.getId();
    }

    @AfterEach
    void cleanUp() {
        deliveries.deleteAll();
        outboxEvents.deleteAll();
        subscriptions.deleteAll();
        alerts.deleteAll();
        detectionRuns.deleteAll();
        seriesRepository.deleteAll();
        orgUnits.deleteAll();
    }

    @Test
    void aSuccessfulDeliveryIsSignedAndCarriesTheStableEventId() {
        wireMock.stubFor(post(urlPathEqualTo("/hook")).willReturn(aResponse().withStatus(200)));
        subscriptions.save(new WebhookSubscription(wireMock.baseUrl() + "/hook", SECRET, null, clock.instant()));
        OutboxEvent event = outboxEvents.save(new OutboxEvent(alertId, AlertStatus.OPEN, """
                {"alertId":%d,"orgUnitName":"Kpanga Kabonde","dataElementName":"IDSR Malaria"}
                """.formatted(alertId), clock.instant()));

        fanoutService.claimAndFanOut();
        dispatchScheduler.dispatchDueDeliveries();

        List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/hook")));
        assertThat(requests).hasSize(1);
        LoggedRequest request = requests.getFirst();
        assertThat(request.getHeader("X-Event-Id")).isEqualTo(String.valueOf(event.getId()));
        String timestamp = request.getHeader("X-Signature-Timestamp");
        String expectedSignature = signer.sign(SECRET, Instant.ofEpochSecond(Long.parseLong(timestamp)), request.getBodyAsString());
        assertThat(request.getHeader("X-Signature")).isEqualTo(expectedSignature);
        assertThat(request.getBodyAsString()).contains("\"eventId\":" + event.getId(), "Kpanga Kabonde", "IDSR Malaria");

        assertThat(deliveries.findAll()).singleElement().satisfies(delivery ->
                assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED));
    }

    @Test
    void aConsistentlyFailingEndpointRetriesThenDeadLettersAfterMaxAttempts() {
        wireMock.stubFor(post(urlPathEqualTo("/hook")).willReturn(aResponse().withStatus(500)));
        subscriptions.save(new WebhookSubscription(wireMock.baseUrl() + "/hook", SECRET, null, clock.instant()));
        outboxEvents.save(new OutboxEvent(alertId, AlertStatus.OPEN, "{}", clock.instant()));

        fanoutService.claimAndFanOut();

        // max-attempts=3: each poll claims the row (its backoff has already
        // passed, since the clock is advanced past it below) and fails it
        // again, until the third failure dead-letters it.
        dispatchScheduler.dispatchDueDeliveries();
        assertThat(deliveries.findAll()).singleElement().satisfies(delivery -> {
            assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
            assertThat(delivery.getAttempts()).isEqualTo(1);
        });

        clock.advance(Duration.ofSeconds(2));
        dispatchScheduler.dispatchDueDeliveries();
        assertThat(deliveries.findAll()).singleElement().satisfies(delivery -> {
            assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
            assertThat(delivery.getAttempts()).isEqualTo(2);
        });

        clock.advance(Duration.ofSeconds(2));
        dispatchScheduler.dispatchDueDeliveries();
        assertThat(deliveries.findAll()).singleElement().satisfies(delivery -> {
            assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DEAD_LETTERED);
            assertThat(delivery.getAttempts()).isEqualTo(3);
            assertThat(delivery.getLastError()).contains("500");
        });

        assertThat(wireMock.findAll(postRequestedFor(urlPathEqualTo("/hook")))).hasSize(3);
        assertThat(meterRegistry.get("outbreak.webhook.dispatch.failures").tag("reason", "http-500").counter().count())
                .isEqualTo(3);
    }

    @Test
    void aDeliveryStuckSendingPastTheStaleTimeoutIsReclaimable() {
        subscriptions.save(new WebhookSubscription(wireMock.baseUrl() + "/hook", SECRET, null, clock.instant()));
        OutboxEvent event = outboxEvents.save(new OutboxEvent(alertId, AlertStatus.OPEN, "{}", clock.instant()));
        WebhookSubscription subscription = subscriptions.findAll().getFirst();
        WebhookDelivery stuck = deliveries.save(new WebhookDelivery(event.getId(), subscription.getId(), clock.instant()));
        stuck.markSending(clock.instant());
        deliveries.save(stuck);

        // Still within the stale window: not claimable yet.
        assertThat(deliveries.findClaimableIds(clock.instant(), clock.instant().minusSeconds(290), 10)).isEmpty();

        // Past the configured 300s stale-SENDING timeout (the default):
        // the instance that claimed it is presumed crashed mid-send.
        clock.advance(Duration.ofSeconds(301));
        assertThat(deliveries.findClaimableIds(clock.instant(), clock.instant().minusSeconds(300), 10))
                .containsExactly(stuck.getId());
    }

    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-10-19T03:00:00Z"));
        }
    }

    static class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
