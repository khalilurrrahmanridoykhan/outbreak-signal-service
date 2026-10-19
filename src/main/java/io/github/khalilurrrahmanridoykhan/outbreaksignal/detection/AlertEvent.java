package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One status change an alert went through. Append-only: a row is written
 * every time {@link Alert#transitionTo} succeeds, and never edited or
 * deleted afterward, so {@code GET /alerts/{id}} can always show the full,
 * honest history of who reviewed an alert and when.
 */
@Entity
@Table(name = "alert_event")
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 16)
    private AlertStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    private AlertStatus toStatus;

    @Column
    private String note;

    @Column(nullable = false)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AlertEvent() {
    }

    public AlertEvent(Alert alert, AlertStatus fromStatus, AlertStatus toStatus, String note, String actor, Instant createdAt) {
        this.alert = alert;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.actor = actor;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Alert getAlert() {
        return alert;
    }

    public AlertStatus getFromStatus() {
        return fromStatus;
    }

    public AlertStatus getToStatus() {
        return toStatus;
    }

    public String getNote() {
        return note;
    }

    public String getActor() {
        return actor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
