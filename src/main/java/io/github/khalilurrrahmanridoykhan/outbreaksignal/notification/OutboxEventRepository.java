package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims up to {@code batchSize} pending rows for this transaction with a
     * real row lock, oldest first. {@code FOR UPDATE SKIP LOCKED} means a
     * second poller (another instance, or another thread racing this one in a
     * test) reading concurrently simply skips whatever this transaction has
     * already locked, rather than blocking on it or claiming it too — that is
     * what makes concurrent pollers never double-claim a row. The caller must
     * mark these rows {@link OutboxEventStatus#DISPATCHED} (via
     * {@link #markDispatched}) before this transaction commits, since the
     * lock — and with it, the claim — is released at commit regardless.
     */
    @Query(value = """
            SELECT id FROM outbox_event
            WHERE status = 'PENDING'
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findClaimableIds(@Param("batchSize") int batchSize);

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = io.github.khalilurrrahmanridoykhan.outbreaksignal.notification.OutboxEventStatus.DISPATCHED, e.dispatchedAt = :now WHERE e.id IN :ids")
    void markDispatched(@Param("ids") List<Long> ids, @Param("now") Instant now);

    /** For the {@code outbreak.outbox.lag.seconds} gauge: null when nothing is pending. */
    @Query("SELECT MIN(e.createdAt) FROM OutboxEvent e WHERE e.status = io.github.khalilurrrahmanridoykhan.outbreaksignal.notification.OutboxEventStatus.PENDING")
    Instant findOldestPendingCreatedAt();
}
