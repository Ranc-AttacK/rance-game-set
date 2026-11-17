package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;

class LoginIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    JwtDecoder jwtDecoder;

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
}
