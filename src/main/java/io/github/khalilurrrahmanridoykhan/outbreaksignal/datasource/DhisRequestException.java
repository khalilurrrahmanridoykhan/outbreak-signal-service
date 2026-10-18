package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

/**
 * DHIS2 rejected the request itself (4xx: bad credentials, a data element
 * that does not exist, a malformed query). Retrying would send the same bad
 * request again, so this is never retried.
 */
public class DhisRequestException extends DhisClientException {

    public DhisRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
