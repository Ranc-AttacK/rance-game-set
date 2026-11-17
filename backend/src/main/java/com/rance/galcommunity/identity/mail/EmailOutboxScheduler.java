package com.rance.galcommunity.identity.mail;

import com.rance.galcommunity.identity.service.EmailOutboxProcessor;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class EmailOutboxScheduler {

    private final EmailOutboxProcessor processor;

    public EmailOutboxScheduler(EmailOutboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${app.email.poll-interval-ms:5000}")
    public void run() {
        processor.processDue(Instant.now());
    }
}
