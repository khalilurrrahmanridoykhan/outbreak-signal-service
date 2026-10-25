package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    List<NotificationRecipient> findByOrgUnitId(Long orgUnitId);

    List<NotificationRecipient> findAllByOrderByOrgUnitIdAscEmailAsc();
}
