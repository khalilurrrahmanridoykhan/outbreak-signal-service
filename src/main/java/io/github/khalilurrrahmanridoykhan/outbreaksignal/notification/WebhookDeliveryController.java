package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only visibility into delivery attempts — the plan's "dead-letter status ... visible through the API". */
@RestController
@RequestMapping("/api/v1/webhook-deliveries")
class WebhookDeliveryController {

    private final WebhookDeliveryRepository deliveries;

    WebhookDeliveryController(WebhookDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    Page<WebhookDeliverySummary> list(
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<WebhookDelivery> page = status == null
                ? deliveries.findAll(pageable)
                : deliveries.findByStatus(status, pageable);
        return page.map(WebhookDeliverySummary::from);
    }
}
