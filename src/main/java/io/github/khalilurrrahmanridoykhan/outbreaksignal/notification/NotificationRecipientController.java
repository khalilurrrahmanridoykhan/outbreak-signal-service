package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Org-unit-scoped email recipient CRUD — admin only, same bar as webhook subscriptions. */
@RestController
@RequestMapping("/api/v1/notification-recipients")
class NotificationRecipientController {

    private final NotificationRecipientService service;

    NotificationRecipientController(NotificationRecipientService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    NotificationRecipientSummary create(@Valid @RequestBody CreateNotificationRecipientRequest request) {
        return service.create(request.orgUnitId(), request.email());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<NotificationRecipientSummary> list(@RequestParam(required = false) Long orgUnitId) {
        return service.list(orgUnitId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
