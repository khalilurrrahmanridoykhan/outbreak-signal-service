package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import tools.jackson.databind.ObjectMapper;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import java.util.LinkedHashSet;
import java.util.Set;
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
 *
 * <p>Recipients are the fixed {@code outbreak.notification.email.to}
 * address (always, if email is enabled at all) plus every
 * {@link NotificationRecipient} registered for the alert's own org unit —
 * additive, not a replacement, so a general ops address can keep seeing
 * everything while a district officer only sees their own area.
 */
@Component
class AlertEmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(AlertEmailNotifier.class);

    private final JavaMailSender mailSender;
    private final OutbreakProperties properties;
    private final ObjectMapper objectMapper;
    private final NotificationRecipientRepository recipients;

    AlertEmailNotifier(
            JavaMailSender mailSender,
            OutbreakProperties properties,
            ObjectMapper objectMapper,
            NotificationRecipientRepository recipients) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.recipients = recipients;
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
            message.setTo(resolveRecipients(config, payload.orgUnitId()));
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

    /** The fixed ops address plus every org-unit-specific recipient, de-duplicated. */
    private String[] resolveRecipients(OutbreakProperties.Notification.Email config, Long orgUnitId) {
        Set<String> addresses = new LinkedHashSet<>();
        addresses.add(config.to());
        recipients.findByOrgUnitId(orgUnitId).forEach(r -> addresses.add(r.getEmail()));
        return addresses.toArray(new String[0]);
    }
}
