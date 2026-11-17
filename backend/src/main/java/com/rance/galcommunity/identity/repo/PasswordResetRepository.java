package com.rance.galcommunity.identity.repo;

import com.rance.galcommunity.identity.domain.PasswordReset;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

    Optional<PasswordReset> findByTokenHash(String tokenHash);

    @Query("select r.userId from PasswordReset r where r.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PasswordReset r where r.tokenHash = :tokenHash")
    Optional<PasswordReset> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordReset r set r.usedAt = :now where r.userId = :userId and r.usedAt is null")
    int expireActive(@Param("userId") Long userId, @Param("now") Instant now);
}
