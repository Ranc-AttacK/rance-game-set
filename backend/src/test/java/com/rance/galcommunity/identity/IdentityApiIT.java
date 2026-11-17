package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.api.IdentityApi;
import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IdentityApiIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    IdentityApi identityApi;

    private UserAccount create(String email, AccountStatus status) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setNickname("N-" + email);
        user.setRole(Role.USER);
        user.setStatus(status);
        user.setEmailVerified(true);
        return users.saveAndFlush(user);
    }

    @Test
    void returnsOnlyActiveUsersInBatch() {
        UserAccount first = create("api1@example.com", AccountStatus.ACTIVE);
        UserAccount second = create("api2@example.com", AccountStatus.ACTIVE);
        UserAccount banned = create("api3@example.com", AccountStatus.BANNED);

        Map<Long, com.rance.galcommunity.identity.api.UserSummary> result =
                identityApi.findUsers(List.of(first.getId(), second.getId(), banned.getId()));

        assertThat(result).hasSize(2);
        assertThat(result.get(first.getId()).nickname()).isEqualTo("N-api1@example.com");
        assertThat(identityApi.findUser(banned.getId())).isEmpty();
    }
}
