package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.EmailTemplate;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import com.rance.galcommunity.identity.repo.PasswordResetRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.identity.service.AuthService;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.util.Hashing;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class PasswordResetIT extends IntegrationTestBase {

    @Autowired
    EmailOutboxRepository outbox;

    @Autowired
    PasswordResetRepository resets;

    @Autowired
    UserAccountRepository users;

    @Autowired
    AuthService authService;

    private String registerRaw(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "nickname", "Reset", "password", "secret123"))))
                .andExpect(status().isCreated());
        return email;
    }

    private Cookie loginCookie(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("refresh_token");
    }

    private String latestResetToken() {
        return outbox.findAll().stream()
                .filter(entry -> entry.getTemplate() == EmailTemplate.RESET_PASSWORD)
                .reduce((first, second) -> second)
                .orElseThrow()
                .getPayload().get("token").toString();
    }

    @Test
    void forgotAndResetPasswordRevokesSessions() throws Exception {
        String email = "reset1@example.com";
        registerRaw(email);
        Cookie oldCookie = loginCookie(email, "secret123");
        Cookie secondCookie = loginCookie(email, "secret123");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());

        String rawToken = latestResetToken();
        assertThat(rawToken).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", rawToken, "newPassword", "newsecret1"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "secret123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "newsecret1"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(oldCookie).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(secondCookie).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", rawToken, "newPassword", "another123"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nobody@example.com"))))
                .andExpect(status().isOk());
        assertThat(resets.count()).isZero();
        assertThat(outbox.findAll().stream()
                .filter(entry -> entry.getTemplate() == EmailTemplate.RESET_PASSWORD)).isEmpty();
    }

    @Test
    void invalidResetTokenReturns42201() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", "not-a-real-token", "newPassword", "newsecret1"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void expiredResetTokenReturns42201() throws Exception {
        String email = "reset2@example.com";
        registerRaw(email);
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());
        String rawToken = latestResetToken();
        long userId = users.findByEmail(email).orElseThrow().getId();
        jdbcTemplate.update("UPDATE user_password_resets SET expires_at = ? WHERE user_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), userId);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", rawToken, "newPassword", "newsecret1"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void weakNewPasswordReturns40001() throws Exception {
        String email = "reset3@example.com";
        registerRaw(email);
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());
        String rawToken = latestResetToken();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", rawToken, "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void secondForgotInvalidatesOldResetToken() throws Exception {
        String email = "reset4@example.com";
        registerRaw(email);
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());
        String oldToken = latestResetToken();
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());
        String newToken = latestResetToken();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", oldToken, "newPassword", "newsecret1"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", newToken, "newPassword", "newsecret1"))))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentResetWithSameTokenAllowsOnlyOne() throws Exception {
        String email = "reset5@example.com";
        registerRaw(email);
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());
        String rawToken = latestResetToken();

        Callable<Boolean> task = () -> {
            try {
                authService.resetPassword(rawToken, "newsecret1");
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(task);
            Future<Boolean> second = pool.submit(task);
            List<Boolean> results = List.of(first.get(), second.get());
            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
    }
}
