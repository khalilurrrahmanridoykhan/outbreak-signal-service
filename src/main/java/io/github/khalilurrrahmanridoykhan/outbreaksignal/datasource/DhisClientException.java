package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

/** Something went wrong calling DHIS2. See the subclasses for whether it is worth retrying. */
public class DhisClientException extends RuntimeException {

    public DhisClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
