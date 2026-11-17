package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.EmailOutboxEntry;
import com.rance.galcommunity.identity.domain.EmailOutboxStatus;
import com.rance.galcommunity.identity.domain.EmailTemplate;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailOutboxService {

    private final EmailOutboxRepository outbox;

    public EmailOutboxService(EmailOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public void enqueue(String toEmail, EmailTemplate template, Map<String, Object> payload) {
        EmailOutboxEntry entry = new EmailOutboxEntry();
        entry.setToEmail(toEmail);
        entry.setTemplate(template);
        entry.setPayload(payload);
        entry.setStatus(EmailOutboxStatus.PENDING);
        entry.setAttempts(0);
        entry.setNextAttemptAt(Instant.now());
        outbox.save(entry);
    }
}
