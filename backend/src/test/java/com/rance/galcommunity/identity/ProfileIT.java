package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ProfileIT extends IntegrationTestBase {

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
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private Cookie loginCookie(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("refresh_token");
    }

    @Test
    void publicProfileHidesEmailAndSelfProfileShowsIt() throws Exception {
        registerAndGetVerificationToken("profile1@example.com", "Profile", "secret123");
        long id = users.findByEmail("profile1@example.com").orElseThrow().getId();
        String access = loginToken("profile1@example.com", "secret123");

        mockMvc.perform(get("/api/v1/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Profile"))
                .andExpect(jsonPath("$.data.email").doesNotExist());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("profile1@example.com"));
    }

    @Test
    void updateRequiresVerifiedEmailAndChangesFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "profile2@example.com",
                                "nickname", "Before",
                                "password", "secret123"))))
                .andExpect(status().isCreated());
        String access = loginToken("profile2@example.com", "secret123");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "After"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));

        String rawToken = outbox.findAll().stream()
                .filter(entry -> entry.getToEmail().equals("profile2@example.com"))
                .findFirst().orElseThrow()
                .getPayload().get("token").toString();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", rawToken))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nickname", "After", "bio", "hello"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("After"))
                .andExpect(jsonPath("$.data.bio").value("hello"));
    }

    @Test
    void deleteAccountAnonymizesAndRevokesSessions() throws Exception {
        registerAndGetVerificationToken("profile3@example.com", "Delete", "secret123");
        long id = users.findByEmail("profile3@example.com").orElseThrow().getId();
        Cookie refresh = loginCookie("profile3@example.com", "secret123");
        String access = loginToken("profile3@example.com", "secret123");

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        var user = users.findById(id).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(user.getNickname()).isEqualTo("已注销用户");
        assertThat(user.getEmail()).startsWith("deleted-");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "profile3@example.com", "password", "secret123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));
    }
}
