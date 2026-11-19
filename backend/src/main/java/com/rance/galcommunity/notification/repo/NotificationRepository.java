package com.rance.galcommunity.notification.repo;

import com.rance.galcommunity.notification.domain.Notification;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(long recipientId,
                                                                   Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(long recipientId);

    Optional<Notification> findByIdAndRecipientId(long id, long recipientId);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :at
            where n.recipientId = :recipientId and n.readAt is null
            """)
    int markAllRead(@Param("recipientId") long recipientId, @Param("at") Instant at);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
