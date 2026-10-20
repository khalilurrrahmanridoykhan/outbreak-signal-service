package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {

    List<WebhookSubscription> findByActiveTrue();
}
