package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.EmailOutboxEntry;
import com.rance.galcommunity.identity.domain.EmailOutboxStatus;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import com.rance.galcommunity.identity.repo.EmailVerificationRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.shared.util.Hashing;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class RegistrationIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    EmailOutboxRepository outbox;

    @Autowired
    EmailVerificationRepository verifications;

    @Test
    void registersUserAndQueuesVerificationEmail() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "Alice@Example.com",
                                "nickname", "Alice",
                                "password", "secret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("alice@example.com");
        assertThat(users.findByEmail("alice@example.com")).isPresent();

        EmailOutboxEntry email = outbox.findAll().get(0);
        assertThat(email.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        String rawToken = String.valueOf(email.getPayload().get("token"));
        assertThat(rawToken).isNotBlank();
        assertThat(verifications.findByTokenHash(Hashing.sha256(rawToken))).isPresent();
        assertThat(verifications.findAll().get(0).getTokenHash()).isNotEqualTo(rawToken);
    }

    @Test
    void duplicateEmailReturns40901() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "dup@example.com", "nickname", "Dup", "password", "secret123"));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void weakPasswordReturns40001() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "weak@example.com",
                                "nickname", "Weak",
                                "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
