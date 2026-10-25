package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One email address that wants a notification for every alert raised
 * against one org unit, on top of whatever the fixed
 * {@code outbreak.notification.email.to} address already receives — see
 * {@link AlertEmailNotifier}. {@code orgUnitId} is a plain column, not a
 * JPA association: {@code notification} must not depend on
 * {@code observation} (where {@code OrgUnit} lives) — see
 * {@code PackageDependencyTest} — the same reason {@link WebhookDelivery}'s
 * own foreign keys are plain columns too.
 */
@Entity
@Table(name = "notification_recipient")
public class NotificationRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_unit_id", nullable = false)
    private Long orgUnitId;

    @Column(nullable = false)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationRecipient() {
    }

    public NotificationRecipient(Long orgUnitId, String email, Instant createdAt) {
        this.orgUnitId = orgUnitId;
        this.email = email;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
