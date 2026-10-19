package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and reviewed-status transitions for alerts. Every transition writes
 * its {@link AlertEvent} in the same transaction as the status change
 * itself, and flushes before returning — so a concurrent, conflicting review
 * of the same alert fails with Spring's own
 * {@code ObjectOptimisticLockingFailureException} right here, inside this
 * method, rather than silently at the transaction's eventual commit.
 */
@Service
public class AlertService {

    private final AlertRepository alerts;
    private final AlertEventRepository events;
    private final Clock clock;

    AlertService(AlertRepository alerts, AlertEventRepository events, Clock clock) {
        this.alerts = alerts;
        this.events = events;
        this.clock = clock;
    }

    public Page<Alert> search(AlertStatus status, Long orgUnitId, String algorithm, LocalDate from, LocalDate to, Pageable pageable) {
        return alerts.search(status, orgUnitId, algorithm, from, to, pageable);
    }

    public Alert get(Long id) {
        return alerts.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
    }

    public List<AlertEvent> history(Long id) {
        get(id); // 404 if the alert itself does not exist, before returning an empty history for it
        return events.findByAlertIdOrderByCreatedAtAsc(id);
    }

    @Transactional
    public Alert acknowledge(Long id, String actor) {
        return transition(id, AlertStatus.ACKNOWLEDGED, null, actor);
    }

    @Transactional
    public Alert resolve(Long id, String actor) {
        return transition(id, AlertStatus.RESOLVED, null, actor);
    }

    @Transactional
    public Alert dismiss(Long id, String reason, String actor) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to dismiss an alert");
        }
        return transition(id, AlertStatus.DISMISSED, reason, actor);
    }

    private Alert transition(Long id, AlertStatus to, String note, String actor) {
        Alert alert = get(id);
        AlertStatus from = alert.transitionTo(to);
        Alert saved = alerts.saveAndFlush(alert);
        events.save(new AlertEvent(saved, from, to, note, actor, clock.instant()));
        return saved;
    }
}
