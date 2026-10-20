package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Webhook subscription CRUD — admin only, since a subscription receives every matching alert's data. */
@RestController
@RequestMapping("/api/v1/webhook-subscriptions")
class WebhookSubscriptionController {

    private final WebhookSubscriptionService service;

    WebhookSubscriptionController(WebhookSubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    WebhookSubscriptionCreated create(@Valid @RequestBody CreateWebhookSubscriptionRequest request) {
        return service.create(request.url(), request.secret(), request.eventTypes());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<WebhookSubscriptionSummary> list() {
        return service.list();
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}
