package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    /**
     * Claims rows that are due — {@code PENDING} past their backoff, or
     * {@code SENDING} past {@code staleBefore} (meaning whatever dispatcher
     * claimed it previously crashed mid-send and never recorded an outcome) —
     * with the same {@code FOR UPDATE SKIP LOCKED} claim pattern as
     * {@link OutboxEventRepository#findClaimableIds}.
     */
    @Query(value = """
            SELECT id FROM webhook_delivery
            WHERE (status = 'PENDING' AND next_attempt_at <= :now)
               OR (status = 'SENDING' AND updated_at <= :staleBefore)
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findClaimableIds(@Param("now") Instant now, @Param("staleBefore") Instant staleBefore, @Param("batchSize") int batchSize);

    @Modifying
    @Query("UPDATE WebhookDelivery d SET d.status = io.github.khalilurrrahmanridoykhan.outbreaksignal.notification.WebhookDeliveryStatus.SENDING, d.updatedAt = :now WHERE d.id IN :ids")
    void markSending(@Param("ids") List<Long> ids, @Param("now") Instant now);

    Page<WebhookDelivery> findByStatus(WebhookDeliveryStatus status, Pageable pageable);
}
