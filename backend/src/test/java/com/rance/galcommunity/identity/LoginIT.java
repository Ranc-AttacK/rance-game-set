package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.RefreshTokenRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.shared.util.Hashing;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;

class LoginIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    RefreshTokenRepository refreshTokens;

    @Autowired
    PasswordEncoder passwordEncoder;

    private String body(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    @Test
    void loginSetsRefreshCookieAndReturnsAccessToken() throws Exception {
        registerAndGetVerificationToken("login1@example.com", "Login", "secret123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("login1@example.com", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("login1@example.com"))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("refresh_token=").contains("HttpOnly").contains("SameSite=Strict");

        assertThat(setCookie).contains("Path=/api/v1/auth").contains("Max-Age=604800");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("refreshToken");

        var refreshMatcher = Pattern.compile("refresh_token=([^;]+)").matcher(setCookie);
        assertThat(refreshMatcher.find()).isTrue();
        String rawRefresh = refreshMatcher.group(1);
        var stored = refreshTokens.findByTokenHash(Hashing.sha256(rawRefresh)).orElseThrow();
        assertThat(stored.getTokenHash()).isNotEqualTo(rawRefresh);
        assertThat(stored.getFamilyId()).isNotBlank();
        assertThat(stored.getExpiresAt()).isAfter(Instant.now());

        String accessToken = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.accessToken");
        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isNotBlank();
        assertThat(jwt.getClaimAsStringList("roles")).contains("USER");
        assertThat(users.findByEmail("login1@example.com")).isPresent();
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        registerAndGetVerificationToken("login2@example.com", "Login", "secret123");

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("login2@example.com", "wrongpass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("nobody@example.com", "secret123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void loginIsRateLimited() throws Exception {
        registerAndGetVerificationToken("login3@example.com", "Login", "secret123");
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(body("login3@example.com", "wrongpass1")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("login3@example.com", "wrongpass1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901));
    }

    @Test
    void bannedUserCannotLogin() throws Exception {
        UserAccount user = new UserAccount();
        user.setEmail("banned@example.com");
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setNickname("Banned");
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.BANNED);
        user.setEmailVerified(true);
        users.saveAndFlush(user);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("banned@example.com", "secret123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void invalidPayloadReturns40001() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-an-email", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void loginIsCaseInsensitiveForEmail() throws Exception {
        registerAndGetVerificationToken("case@example.com", "Case", "secret123");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("CASE@EXAMPLE.COM", "secret123")))
                .andExpect(status().isOk());
    }
}
