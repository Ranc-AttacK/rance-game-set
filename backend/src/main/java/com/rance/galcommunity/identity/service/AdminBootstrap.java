package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.identity.security.AppProperties;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserAccountRepository users;
    private final AppProperties props;

    public AdminBootstrap(UserAccountRepository users, AppProperties props) {
        this.users = users;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String bootstrapEmail = props.bootstrapAdminEmail();
        if (bootstrapEmail == null || bootstrapEmail.isBlank()) {
            return;
        }
        String normalized = bootstrapEmail.trim().toLowerCase(Locale.ROOT);
        Optional<UserAccount> existing = users.findByEmail(normalized);
        if (existing.isEmpty()) {
            log.warn("Bootstrap admin {} is not registered yet; register the account and restart", normalized);
            return;
        }
        UserAccount user = existing.get();
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (!user.isEmailVerified()) {
            log.error("Refusing to promote {}: email is not verified", normalized);
            return;
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            log.error("Refusing to promote {}: account status is {}", normalized, user.getStatus());
            return;
        }
        if (users.existsByRole(Role.ADMIN)) {
            log.info("An admin already exists; skipping bootstrap promotion for {}", normalized);
            return;
        }
        user.setRole(Role.ADMIN);
        users.save(user);
        log.info("Promoted bootstrap admin account {}", normalized);
    }
}
