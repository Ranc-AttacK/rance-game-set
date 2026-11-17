package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class VerifyEmailIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    EmailOutboxRepository outbox;

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    void verifiesEmailOnce() throws Exception {
        String token = registerAndGetVerificationToken("verify1@example.com", "Verify", "secret123");
        String body = objectMapper.writeValueAsString(Map.of("token", token));

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(users.findByEmail("verify1@example.com").orElseThrow().isEmailVerified()).isTrue();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void resendIsAuthenticatedAndRateLimited() throws Exception {
        registerAndGetVerificationToken("verify2@example.com", "Verify", "secret123");
        String access = loginToken("verify2@example.com", "secret123");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/resend-verification")
                            .header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901));

    }

    @Test
    void resendRejectsAlreadyVerifiedEmail() throws Exception {
        String token = registerAndGetVerificationToken("verify3@example.com", "Verify", "secret123");
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk());
        String access = loginToken("verify3@example.com", "secret123");

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void resendRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-verification"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }
}
