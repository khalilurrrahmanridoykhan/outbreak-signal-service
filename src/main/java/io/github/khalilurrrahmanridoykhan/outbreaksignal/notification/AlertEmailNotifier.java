package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import tools.jackson.databind.ObjectMapper;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends one plain-text email per dispatched event, best-effort — unlike
 * {@link WebhookDelivery}, there is no persisted retry or dead-letter state
 * for email (the plan does not ask for one), so a failure here is only
 * logged, and the only durability guarantee is that it was attempted once,
 * right after the event's transaction committed.
 */
@Component
class AlertEmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(AlertEmailNotifier.class);

    private final JavaMailSender mailSender;
    private final OutbreakProperties properties;
    private final ObjectMapper objectMapper;

    AlertEmailNotifier(JavaMailSender mailSender, OutbreakProperties properties, ObjectMapper objectMapper) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    void notifyIfEnabled(OutboxEvent event) {
        OutbreakProperties.Notification.Email config = properties.notification().email();
        if (!config.enabled()) {
            return;
        }
        try {
            AlertEventPayload payload = objectMapper.readValue(event.getPayload(), AlertEventPayload.class);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(config.from());
            message.setTo(config.to());
            message.setSubject("[Outbreak Signal] %s — %s / %s"
                    .formatted(event.getEventType(), payload.orgUnitName(), payload.dataElementName()));
            message.setText("""
                    Alert #%d moved to %s.

                    Org unit:     %s
                    Data element: %s
                    Algorithm:    %s
                    Period:       %s
                    Score:        %s (threshold %s, severity %s)
                    %s
                    This is a statistical signal for review, not a confirmed outbreak.
                    """.formatted(
                    payload.alertId(), event.getEventType(),
                    payload.orgUnitName(), payload.dataElementName(), payload.algorithm(), payload.periodStart(),
                    payload.score(), payload.threshold(), payload.severity(),
                    payload.note() == null ? "" : "Note:         " + payload.note() + "\n"));
            mailSender.send(message);
        } catch (RuntimeException e) {
            log.warn("Could not send notification email for outbox event {}: {}", event.getId(), e.getMessage());
        }
    }
}
