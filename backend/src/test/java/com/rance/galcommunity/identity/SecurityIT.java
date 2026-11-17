package com.rance.galcommunity.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.identity.service.TokenService;
import com.rance.galcommunity.support.ProbeSecureController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(ProbeSecureController.class)
class SecurityIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    TokenService tokenService;

    private UserAccount createUser(String email, Role role) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setNickname("User");
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        return users.saveAndFlush(user);
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/test/secure/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void acceptsValidToken() throws Exception {
        UserAccount user = createUser("sec1@example.com", Role.USER);
        String token = tokenService.issue(user).value();

        mockMvc.perform(get("/test/secure/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(user.getId().toString()))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void regularUserCannotAccessAdminEndpoint() throws Exception {
        UserAccount user = createUser("sec2@example.com", Role.USER);
        String token = tokenService.issue(user).value();

        mockMvc.perform(get("/api/v1/admin/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void adminCanAccessAdminEndpoint() throws Exception {
        UserAccount admin = createUser("sec3@example.com", Role.ADMIN);
        String token = tokenService.issue(admin).value();

        mockMvc.perform(get("/api/v1/admin/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
