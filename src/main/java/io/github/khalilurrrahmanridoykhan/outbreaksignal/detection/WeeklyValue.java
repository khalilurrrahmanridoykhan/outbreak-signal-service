package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.time.LocalDate;

/**
 * One week's value, in the plain form every detection strategy works with.
 * Deliberately not the JPA {@code Observation} entity: this package has no
 * dependency on persistence or on how the value was obtained, only on the
 * series of numbers and when each one landed.
 */
public record WeeklyValue(LocalDate weekStart, double value) {
}
