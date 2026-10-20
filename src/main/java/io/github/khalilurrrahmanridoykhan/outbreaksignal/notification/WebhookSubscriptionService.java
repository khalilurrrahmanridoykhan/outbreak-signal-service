package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WebhookSubscriptionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookSubscriptionRepository subscriptions;
    private final Clock clock;

    WebhookSubscriptionService(WebhookSubscriptionRepository subscriptions, Clock clock) {
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Transactional
    WebhookSubscriptionCreated create(String url, String requestedSecret, Set<AlertStatus> eventTypes) {
        String secret = (requestedSecret == null || requestedSecret.isBlank()) ? generateSecret() : requestedSecret;
        WebhookSubscription saved = subscriptions.save(new WebhookSubscription(url, secret, eventTypes, clock.instant()));
        return new WebhookSubscriptionCreated(
                saved.getId(), saved.getUrl(), secret, saved.getEventTypes(), saved.isActive(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    List<WebhookSubscriptionSummary> list() {
        return subscriptions.findAll().stream().map(WebhookSubscriptionSummary::from).toList();
    }

    @Transactional
    void deactivate(Long id) {
        WebhookSubscription subscription = subscriptions.findById(id)
                .orElseThrow(() -> new WebhookSubscriptionNotFoundException(id));
        subscription.deactivate();
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
