package com.rance.galcommunity.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailOutboxEntryTest {

    @Test
    void terminalFailureScrubsPayload() {
        EmailOutboxEntry entry = new EmailOutboxEntry();
        entry.setPayload(Map.of("token", "secret"));
        entry.setStatus(EmailOutboxStatus.PENDING);
        entry.setAttempts(0);

        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            entry.markFailed(now);
        }

        assertThat(entry.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(entry.getPayload()).isEmpty();
    }
}
