package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

/**
 * An alert's lifecycle state. Only {@link #OPEN} is used until Phase 4 adds
 * the transitions ({@code acknowledge}/{@code resolve}/{@code dismiss}); the
 * others exist now only because the database constraint already lists them.
 */
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    DISMISSED
}
