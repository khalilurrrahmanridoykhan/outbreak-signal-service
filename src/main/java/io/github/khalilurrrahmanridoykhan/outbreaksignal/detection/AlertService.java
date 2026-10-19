package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and reviewed-status transitions for alerts, always returning plain
 * DTOs rather than the JPA entities themselves. {@code Series} and
 * {@code OrgUnit} are lazy associations on {@link Alert} (and
 * {@code spring.jpa.open-in-view} is deliberately off), so mapping to
 * {@link AlertSummary} has to happen here, inside the method's own
 * transaction, not later in the controller after the session that could
 * load them is already closed — every public method here does exactly that
 * before returning, including the write ones.
 *
 * <p>Every transition writes its {@link AlertEvent} in the same transaction
 * as the status change itself, and flushes before returning — so a
 * concurrent, conflicting review of the same alert fails with Spring's own
 * {@code ObjectOptimisticLockingFailureException} right here, rather than
 * silently at the transaction's eventual commit.
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

    @Transactional(readOnly = true)
    public Page<AlertSummary> search(AlertStatus status, Long orgUnitId, String algorithm, LocalDate from, LocalDate to, Pageable pageable) {
        return alerts.search(status, orgUnitId, algorithm, from, to, pageable).map(AlertSummary::from);
    }

    @Transactional(readOnly = true)
    public AlertDetail getDetail(Long id) {
        Alert alert = find(id);
        List<AlertEventSummary> history = events.findByAlertIdOrderByCreatedAtAsc(id).stream()
                .map(AlertEventSummary::from)
                .toList();
        return new AlertDetail(AlertSummary.from(alert), history);
    }

    @Transactional
    public AlertSummary acknowledge(Long id, String actor) {
        return AlertSummary.from(transition(id, AlertStatus.ACKNOWLEDGED, null, actor));
    }

    @Transactional
    public AlertSummary resolve(Long id, String actor) {
        return AlertSummary.from(transition(id, AlertStatus.RESOLVED, null, actor));
    }

    @Transactional
    public AlertSummary dismiss(Long id, String reason, String actor) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to dismiss an alert");
        }
        return AlertSummary.from(transition(id, AlertStatus.DISMISSED, reason, actor));
    }

    private Alert find(Long id) {
        return alerts.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
    }

    private Alert transition(Long id, AlertStatus to, String note, String actor) {
        Alert alert = find(id);
        AlertStatus from = alert.transitionTo(to);
        Alert saved = alerts.saveAndFlush(alert);
        events.save(new AlertEvent(saved, from, to, note, actor, clock.instant()));
        return saved;
    }
}
