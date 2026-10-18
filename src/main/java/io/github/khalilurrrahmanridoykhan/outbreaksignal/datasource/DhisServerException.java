package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

/**
 * DHIS2 itself failed, or could not be reached at all (5xx, a connection
 * error, a timeout, or the circuit breaker refusing to try). This is the one
 * category the sync retries.
 */
public class DhisServerException extends DhisClientException {

    public DhisServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
