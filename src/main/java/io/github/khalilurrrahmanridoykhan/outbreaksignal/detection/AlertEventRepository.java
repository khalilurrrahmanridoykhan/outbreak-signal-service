package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

    List<AlertEvent> findByAlertIdOrderByCreatedAtAsc(Long alertId);
}
