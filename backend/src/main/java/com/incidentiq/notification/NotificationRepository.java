package com.incidentiq.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = "actor")
    List<Notification> findByRecipientIdOrderByIdDesc(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    List<Notification> findByRecipientIdAndReadAtIsNullOrderByIdDesc(UUID recipientId, Pageable pageable);

    /** Marks one notification read, only if it belongs to the recipient. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n set n.readAt = :now
            where n.id = :id and n.recipient.id = :recipientId and n.readAt is null
            """)
    int markRead(
            @Param("id") Long id,
            @Param("recipientId") UUID recipientId,
            @Param("now") Instant now);

    boolean existsByIdAndRecipientId(Long id, UUID recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n set n.readAt = :now
            where n.recipient.id = :recipientId and n.readAt is null
            """)
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);
}
