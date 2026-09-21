package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** The value of a series for one ISO week, identified by the Monday that starts the week. */
@Entity
@Table(name = "observation", uniqueConstraints = @UniqueConstraint(
        name = "uq_observation_series_week", columnNames = {"series_id", "week_start"}))
public class Observation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal value;

    @Column(name = "fetched_at", nullable = false, insertable = false, updatable = false)
    private Instant fetchedAt;

    protected Observation() {
    }

    public Observation(Series series, LocalDate weekStart, BigDecimal value) {
        this.series = series;
        this.weekStart = weekStart;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public Series getSeries() {
        return series;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public BigDecimal getValue() {
        return value;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
