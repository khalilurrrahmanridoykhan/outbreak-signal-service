package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.util.List;

/** {@code GET /alerts/{id}}: the alert plus its full reviewed history, oldest first. */
public record AlertDetail(AlertSummary alert, List<AlertEventSummary> history) {
}
