package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import java.time.Instant;

/** {@code GET /notification-recipients}. */
public record NotificationRecipientSummary(Long id, Long orgUnitId, String email, Instant createdAt) {

    static NotificationRecipientSummary from(NotificationRecipient recipient) {
        return new NotificationRecipientSummary(
                recipient.getId(), recipient.getOrgUnitId(), recipient.getEmail(), recipient.getCreatedAt());
    }
}
