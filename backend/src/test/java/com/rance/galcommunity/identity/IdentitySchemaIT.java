package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.EmailOutboxEntry;
import com.rance.galcommunity.identity.domain.EmailOutboxStatus;
import com.rance.galcommunity.identity.domain.EmailTemplate;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class IdentitySchemaIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    EmailOutboxRepository outbox;

    @Test
    void savesAndFindsUserByEmail() {
        UserAccount user = new UserAccount();
        user.setEmail("alice@example.com");
        user.setPasswordHash("hash");
        user.setNickname("Alice");
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(false);
        users.saveAndFlush(user);

        assertThat(users.findByEmail("alice@example.com")).isPresent();
        assertThat(users.findById(user.getId())).isPresent();
        assertThat(user.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void emailMustBeUnique() {
        UserAccount first = new UserAccount();
        first.setEmail("dup@example.com");
        first.setPasswordHash("hash");
        first.setNickname("First");
        first.setRole(Role.USER);
        first.setStatus(AccountStatus.ACTIVE);
        first.setEmailVerified(false);
        users.saveAndFlush(first);

        UserAccount second = new UserAccount();
        second.setEmail("dup@example.com");
        second.setPasswordHash("hash");
        second.setNickname("Second");
        second.setRole(Role.USER);
        second.setStatus(AccountStatus.ACTIVE);
        second.setEmailVerified(false);

        assertThatThrownBy(() -> users.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesAndLoadsEmailOutboxJsonPayload() {
        EmailOutboxEntry entry = new EmailOutboxEntry();
        entry.setToEmail("outbox@example.com");
        entry.setTemplate(EmailTemplate.VERIFY_EMAIL);
        entry.setPayload(Map.of("token", "raw-token-123"));
        entry.setStatus(EmailOutboxStatus.PENDING);
        entry.setAttempts(0);
        entry.setNextAttemptAt(Instant.now());
        outbox.saveAndFlush(entry);

        EmailOutboxEntry loaded = outbox.findById(entry.getId()).orElseThrow();
        assertThat(loaded.getTemplate()).isEqualTo(EmailTemplate.VERIFY_EMAIL);
        assertThat(loaded.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(loaded.getPayload()).containsEntry("token", "raw-token-123");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }
}
