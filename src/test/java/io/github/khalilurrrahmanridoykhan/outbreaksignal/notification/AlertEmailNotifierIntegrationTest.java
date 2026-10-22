package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import tools.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.TestcontainersConfiguration;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.Severity;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves the real {@code spring.mail.*}/Boot wiring, not just
 * {@link AlertEmailNotifier}'s own logic: a real (in-memory) SMTP server
 * receives a real message built from a real {@link OutboxEvent}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AlertEmailNotifierIntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        // The fixed port on ServerSetupTest.SMTP itself (3025), not
        // greenMail.getSmtp().getPort(): @RegisterExtension's GreenMail
        // server is not guaranteed to be running yet when @DynamicPropertySource
        // methods are evaluated, so reading its bound port here can race.
        registry.add("spring.mail.port", ServerSetupTest.SMTP::getPort);
        registry.add("outbreak.notification.email.enabled", () -> "true");
        registry.add("outbreak.notification.email.to", () -> "ops@example.org");
        registry.add("outbreak.notification.email.from", () -> "outbreak-signal@example.org");
    }

    @Autowired
    private AlertEmailNotifier notifier;
    @Autowired
    private OutbreakProperties properties;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sendsAPlainTextEmailDescribingTheAlert() throws Exception {
        OutboxEvent event = new OutboxEvent(1L, AlertStatus.ACKNOWLEDGED, toJson(
                AlertStatus.ACKNOWLEDGED, null, "analyst@example.org"), Instant.now());

        notifier.notifyIfEnabled(event);

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).contains("ACKNOWLEDGED", "Kpanga Kabonde", "IDSR Malaria");
        String body = GreenMailUtil.getBody(received[0]);
        assertThat(body).contains("IDSR Malaria", "EARS_C1", "9.5", "statistical signal for review");
    }

    @Test
    void sendsNothingWhenEmailIsDisabled() {
        JavaMailSender neverCalled = mock(JavaMailSender.class);
        OutbreakProperties disabled = withEmailEnabled(properties, false);
        OutboxEvent event = new OutboxEvent(1L, AlertStatus.DISMISSED, toJson(
                AlertStatus.DISMISSED, "false alarm", "analyst@example.org"), Instant.now());

        new AlertEmailNotifier(neverCalled, disabled, objectMapper).notifyIfEnabled(event);

        verifyNoInteractions(neverCalled);
    }

    private String toJson(AlertStatus status, String note, String actor) {
        AlertEventPayload payload = new AlertEventPayload(
                1L, 2L, "Kpanga Kabonde", "IDSR Malaria", "EARS_C1", LocalDate.of(2026, 1, 5),
                new BigDecimal("9.5"), new BigDecimal("3.0"), Severity.HIGH, status, note, actor);
        return objectMapper.writeValueAsString(payload);
    }

    private static OutbreakProperties withEmailEnabled(OutbreakProperties properties, boolean enabled) {
        OutbreakProperties.Notification.Email email = new OutbreakProperties.Notification.Email(
                enabled, properties.notification().email().to(), properties.notification().email().from());
        OutbreakProperties.Notification notification = new OutbreakProperties.Notification(
                properties.notification().outbox(), properties.notification().webhook(), email);
        return new OutbreakProperties(
                properties.sync(), properties.dhis2(), properties.detection(), properties.security(), notification,
                properties.rateLimit(), properties.request());
    }
}
