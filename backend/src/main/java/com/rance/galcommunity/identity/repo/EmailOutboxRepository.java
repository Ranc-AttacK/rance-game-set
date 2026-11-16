package com.rance.galcommunity.identity.repo;

import com.rance.galcommunity.identity.domain.EmailOutboxEntry;
import com.rance.galcommunity.identity.domain.EmailOutboxStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntry, Long> {
    List<EmailOutboxEntry> findTop20ByStatusAndNextAttemptAtBeforeOrderByIdAsc(
            EmailOutboxStatus status, Instant now);
}
