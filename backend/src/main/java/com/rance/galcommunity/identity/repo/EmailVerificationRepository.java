package com.rance.galcommunity.identity.repo;

import com.rance.galcommunity.identity.domain.EmailVerification;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailVerification v set v.usedAt = :now where v.userId = :userId and v.usedAt is null")
    int expireActive(@Param("userId") Long userId, @Param("now") Instant now);
}
