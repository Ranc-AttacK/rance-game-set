package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class RefreshIT extends IntegrationTestBase {

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
}
