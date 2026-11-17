package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.repo.RefreshTokenRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.identity.service.AuthService;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.util.Hashing;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class RefreshIT extends IntegrationTestBase {

    @Autowired
    RefreshTokenRepository refreshTokens;

    @Autowired
    UserAccountRepository users;

    @Autowired
    AuthService authService;

    private Cookie login(String email) throws Exception {
        registerAndGetVerificationToken(email, "Refresh", "secret123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @Test
    void refreshRotatesCookie() throws Exception {
        Cookie first = login("refresh1@example.com");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(first)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = result.getResponse().getCookie("refresh_token");
        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
    }

    @Test
    void reusingOldRefreshRevokesWholeFamily() throws Exception {
        Cookie first = login("refresh2@example.com");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(first).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk()).andReturn();
        Cookie second = result.getResponse().getCookie("refresh_token");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(first).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(second).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));
    }

    @Test
    void logoutInvalidatesRefresh() throws Exception {
        Cookie cookie = login("refresh3@example.com");
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(cookie).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));
    }

    @Test
    void rejectsForeignOrigin() throws Exception {
        Cookie cookie = login("refresh4@example.com");
        // The Spring Security CORS filter rejects the disallowed origin with a 403 and a
        // plain-text "Invalid CORS request" body before the controller runs, so no API
        // envelope is produced. The essential security guarantee is the 403 itself.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie).header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshWithoutCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));
    }

    @Test
    void expiredRefreshIsRejected() throws Exception {
        Cookie cookie = login("refresh5@example.com");
        var token = refreshTokens.findByTokenHash(Hashing.sha256(cookie.getValue())).orElseThrow();
        token.setExpiresAt(Instant.now().minusSeconds(60));
        refreshTokens.saveAndFlush(token);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));
    }

    @Test
    void bannedUserRefreshIsRejectedAndFamilyRevoked() throws Exception {
        Cookie cookie = login("refresh6@example.com");
        var user = users.findByEmail("refresh6@example.com").orElseThrow();
        user.setStatus(AccountStatus.BANNED);
        users.saveAndFlush(user);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));
    }

    @Test
    void concurrentRefreshWithSameTokenAllowsOnlyOne() throws Exception {
        Cookie cookie = login("refresh7@example.com");
        String raw = cookie.getValue();
        AtomicReference<String> successRaw = new AtomicReference<>();

        Callable<Boolean> task = () -> {
            try {
                AuthService.LoginResult result = authService.refresh(raw);
                successRaw.set(result.refreshToken());
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

        assertThat(successRaw.get()).isNotBlank();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> authService.refresh(successRaw.get()))
                .isInstanceOf(BusinessException.class);
    }
}
