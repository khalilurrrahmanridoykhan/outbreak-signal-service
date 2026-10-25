package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class NotificationRecipientService {

    private final NotificationRecipientRepository recipients;
    private final Clock clock;

    NotificationRecipientService(NotificationRecipientRepository recipients, Clock clock) {
        this.recipients = recipients;
        this.clock = clock;
    }

    @Transactional
    NotificationRecipientSummary create(Long orgUnitId, String email) {
        // A bad orgUnitId surfaces as the org_unit foreign key failing, not
        // a lookup here: notification must not depend on observation (where
        // OrgUnit lives) -- see PackageDependencyTest. ApiExceptionHandler's
        // own DataIntegrityViolationException handler turns that into a
        // clean 409, the same way every other cross-package reference in
        // this codebase that can't be validated in-process already does.
        NotificationRecipient saved = recipients.save(new NotificationRecipient(orgUnitId, email, clock.instant()));
        return NotificationRecipientSummary.from(saved);
    }

    @Transactional(readOnly = true)
    List<NotificationRecipientSummary> list(Long orgUnitId) {
        List<NotificationRecipient> found = orgUnitId == null
                ? recipients.findAllByOrderByOrgUnitIdAscEmailAsc()
                : recipients.findByOrgUnitId(orgUnitId);
        return found.stream().map(NotificationRecipientSummary::from).toList();
    }

    @Transactional
    void delete(Long id) {
        if (!recipients.existsById(id)) {
            throw new NotificationRecipientNotFoundException(id);
        }
        recipients.deleteById(id);
    }
}
