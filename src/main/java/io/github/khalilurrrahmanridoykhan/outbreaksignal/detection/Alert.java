package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Series;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One (series, period, algorithm) that crossed that algorithm's threshold.
 * {@code status} starts at {@code OPEN} and moves only through
 * {@link #transitionTo}, which enforces {@link AlertTransitions}; the
 * {@code @Version} column means a save against a stale copy of this alert
 * (two people reviewing it at once) fails rather than silently overwriting
 * the other's change.
 */
@Entity
@Table(name = "alert", uniqueConstraints = @UniqueConstraint(
        name = "uq_alert_series_period_algorithm", columnNames = {"series_id", "period_start", "algorithm"}))
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false, length = 32)
    private String algorithm;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal score;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertStatus status = AlertStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detection_run_id", nullable = false)
    private DetectionRun detectionRun;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Alert() {
    }

    public Alert(Series series, LocalDate periodStart, String algorithm, BigDecimal score, BigDecimal threshold,
            Severity severity, DetectionRun detectionRun, Instant createdAt) {
        this.series = series;
        this.periodStart = periodStart;
        this.algorithm = algorithm;
        this.score = score;
        this.threshold = threshold;
        this.severity = severity;
        this.detectionRun = detectionRun;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Series getSeries() {
        return series;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public BigDecimal getScore() {
        return score;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public Severity getSeverity() {
        return severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public DetectionRun getDetectionRun() {
        return detectionRun;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    /**
     * Moves this alert to {@code to}, throwing {@link IllegalAlertTransitionException}
     * if that move is not one {@link AlertTransitions} allows from the current status.
     *
     * @return the status this alert was in before the move, for the caller to record as an {@code AlertEvent}
     */
    public AlertStatus transitionTo(AlertStatus to) {
        AlertTransitions.requireAllowed(status, to);
        AlertStatus from = status;
        status = to;
        return from;
    }
}
