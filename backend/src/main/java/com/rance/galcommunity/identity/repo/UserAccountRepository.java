package com.rance.galcommunity.identity.repo;

import com.rance.galcommunity.identity.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
}
