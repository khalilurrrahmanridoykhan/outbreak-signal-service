package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

/**
 * Everything one HTTP delivery attempt needs, resolved inside the claiming
 * transaction so the actual send — a potentially slow network call — never
 * has to read a JPA entity (and risk a lazy-loading failure) outside it.
 */
record WebhookDispatchJob(Long deliveryId, String url, String secret, Long eventId, String body, int attemptsSoFar) {
}
