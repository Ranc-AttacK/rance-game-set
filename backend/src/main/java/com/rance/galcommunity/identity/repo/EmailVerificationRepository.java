package com.rance.galcommunity.identity.repo;

import com.rance.galcommunity.identity.domain.EmailVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByTokenHash(String tokenHash);
}
