package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.EmailOutboxEntry;
import com.rance.galcommunity.identity.domain.EmailOutboxStatus;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class EmailDispatchIT extends IntegrationTestBase {

    @Autowired
    EmailOutboxRepository outbox;

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "nickname", "Mail", "password", "secret123"))))
                .andExpect(status().isCreated());
    }

    @Test
    void sendsPendingEmailAndScrubsPayload() throws Exception {
        register("mail1@example.com");
        int processed = emailOutboxProcessor.processDue(Instant.now());
        assertThat(processed).isEqualTo(1);
        verify(emailSender).send(eq("mail1@example.com"), eq("验证你的邮箱"),
                contains("/verify-email?token="));

        EmailOutboxEntry entry = outbox.findAll().get(0);
        assertThat(entry.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(entry.getPayload()).isEmpty();
    }

    @Test
    void retriesWithBackoffAndFailsAfterFiveAttempts() throws Exception {
        doThrow(new RuntimeException("smtp down"))
                .when(emailSender).send(anyString(), anyString(), anyString());
        register("mail2@example.com");

        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            emailOutboxProcessor.processDue(now);
            now = now.plus(Duration.ofDays(1));
        }

        EmailOutboxEntry entry = outbox.findAll().get(0);
        assertThat(entry.getAttempts()).isEqualTo(5);
        assertThat(entry.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(entry.getPayload()).isEmpty();
    }
}
