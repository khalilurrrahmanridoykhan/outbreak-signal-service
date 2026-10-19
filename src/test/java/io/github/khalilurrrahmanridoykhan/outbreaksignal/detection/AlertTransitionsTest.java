package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class AlertTransitionsTest {

    @ParameterizedTest
    @CsvSource({
            "OPEN, ACKNOWLEDGED",
            "OPEN, DISMISSED",
            "ACKNOWLEDGED, RESOLVED",
            "ACKNOWLEDGED, DISMISSED",
    })
    void allowsEveryMoveTheDiagramShows(AlertStatus from, AlertStatus to) {
        assertThat(AlertTransitions.isAllowed(from, to)).isTrue();
        AlertTransitions.requireAllowed(from, to); // must not throw
    }

    @ParameterizedTest
    @CsvSource({
            "OPEN, RESOLVED", // skipping acknowledgement entirely
            "ACKNOWLEDGED, OPEN", // no going backwards
            "RESOLVED, OPEN",
            "RESOLVED, ACKNOWLEDGED",
            "RESOLVED, DISMISSED",
            "DISMISSED, OPEN",
            "DISMISSED, ACKNOWLEDGED",
            "DISMISSED, RESOLVED",
    })
    void rejectsEveryOtherMove(AlertStatus from, AlertStatus to) {
        assertThat(AlertTransitions.isAllowed(from, to)).isFalse();
        assertThatThrownBy(() -> AlertTransitions.requireAllowed(from, to))
                .isInstanceOf(IllegalAlertTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(AlertStatus.class)
    void aStatusNeverTransitionsToItself(AlertStatus status) {
        assertThat(AlertTransitions.isAllowed(status, status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AlertStatus.class, names = {"RESOLVED", "DISMISSED"})
    void terminalStatusesAllowNoMoveAtAll(AlertStatus terminal) {
        for (AlertStatus candidate : AlertStatus.values()) {
            assertThat(AlertTransitions.isAllowed(terminal, candidate)).isFalse();
        }
    }
}
