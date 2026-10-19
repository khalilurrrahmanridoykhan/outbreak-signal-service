package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

/**
 * The alert lifecycle's only allowed moves:
 *
 * <pre>
 * OPEN ──acknowledge──▶ ACKNOWLEDGED ──resolve──▶ RESOLVED
 *   │                        │
 *   └──────dismiss───────────┴──▶ DISMISSED
 * </pre>
 *
 * {@code RESOLVED} and {@code DISMISSED} are terminal: nothing moves out of
 * either. Every other combination, including a status transitioning to
 * itself, is illegal.
 */
public final class AlertTransitions {

    private AlertTransitions() {
    }

    public static boolean isAllowed(AlertStatus from, AlertStatus to) {
        return switch (from) {
            case OPEN -> to == AlertStatus.ACKNOWLEDGED || to == AlertStatus.DISMISSED;
            case ACKNOWLEDGED -> to == AlertStatus.RESOLVED || to == AlertStatus.DISMISSED;
            case RESOLVED, DISMISSED -> false;
        };
    }

    public static void requireAllowed(AlertStatus from, AlertStatus to) {
        if (!isAllowed(from, to)) {
            throw new IllegalAlertTransitionException(from, to);
        }
    }
}
