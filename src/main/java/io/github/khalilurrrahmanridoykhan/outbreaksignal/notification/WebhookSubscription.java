package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

/**
 * A target URL that wants a signed HTTP callback for some subset of alert
 * events. {@code eventTypes} is stored as a short comma-separated list rather
 * than a join table — it is set once at creation, almost never changed, and
 * never filtered on in a query, so the normal-form cost would buy nothing.
 * {@code null} or empty means every event type.
 */
@Entity
@Table(name = "webhook_subscription")
public class WebhookSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false)
    private String secret;

    @Column(name = "event_types")
    private String eventTypes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WebhookSubscription() {
    }

    public WebhookSubscription(String url, String secret, Set<AlertStatus> eventTypes, Instant createdAt) {
        this.url = url;
        this.secret = secret;
        this.eventTypes = (eventTypes == null || eventTypes.isEmpty())
                ? null
                : String.join(",", eventTypes.stream().map(Enum::name).toList());
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getSecret() {
        return secret;
    }

    public Set<AlertStatus> getEventTypes() {
        if (eventTypes == null || eventTypes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(eventTypes.split(",")).map(AlertStatus::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void deactivate() {
        active = false;
    }

    /** Whether this subscription wants a callback for {@code eventType} — every type, if none were listed. */
    public boolean wants(AlertStatus eventType) {
        Set<AlertStatus> types = getEventTypes();
        return types.isEmpty() || types.contains(eventType);
    }
}
