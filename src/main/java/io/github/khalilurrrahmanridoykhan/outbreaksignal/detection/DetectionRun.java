package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One strategy's execution over every series, start to finish, with the
 * parameters it used — what makes every alert it produced reproducible.
 */
@Entity
@Table(name = "detection_run")
public class DetectionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String algorithm;

    @Column(name = "params_json", nullable = false)
    private String paramsJson;

    @Column(name = "window_weeks", nullable = false)
    private int windowWeeks;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "series_evaluated", nullable = false)
    private int seriesEvaluated;

    @Column(name = "alerts_created", nullable = false)
    private int alertsCreated;

    protected DetectionRun() {
    }

    public DetectionRun(String algorithm, String paramsJson, int windowWeeks, Instant startedAt) {
        this.algorithm = algorithm;
        this.paramsJson = paramsJson;
        this.windowWeeks = windowWeeks;
        this.startedAt = startedAt;
    }

    /** Records the run's outcome. Called once, when the strategy has finished every series. */
    public void finish(Instant finishedAt, int seriesEvaluated, int alertsCreated) {
        this.finishedAt = finishedAt;
        this.seriesEvaluated = seriesEvaluated;
        this.alertsCreated = alertsCreated;
    }

    public Long getId() {
        return id;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public int getWindowWeeks() {
        return windowWeeks;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getSeriesEvaluated() {
        return seriesEvaluated;
    }

    public int getAlertsCreated() {
        return alertsCreated;
    }
}
