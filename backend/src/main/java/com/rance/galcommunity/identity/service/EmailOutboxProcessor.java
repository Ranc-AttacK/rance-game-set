package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.EmailOutboxEntry;
import com.rance.galcommunity.identity.domain.EmailOutboxStatus;
import com.rance.galcommunity.identity.mail.EmailSender;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import com.rance.galcommunity.identity.security.AppProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dispatches due outbox emails.
 *
 * <p>At-least-once: if persisting SENT fails after a successful send, the row stays
 * PENDING and may be sent again. Single-instance assumption: there is no row claim or
 * SKIP LOCKED; run a single scheduler instance for now.
 */
@Service
public class EmailOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxProcessor.class);

    private final EmailOutboxRepository outbox;
    private final EmailSender emailSender;
    private final AppProperties props;

    public EmailOutboxProcessor(EmailOutboxRepository outbox, EmailSender emailSender, AppProperties props) {
        this.outbox = outbox;
        this.emailSender = emailSender;
        this.props = props;
    }

    public int processDue(Instant now) {
        List<Long> ids = outbox
                .findTop20ByStatusAndNextAttemptAtBeforeOrderByIdAsc(EmailOutboxStatus.PENDING, now)
                .stream()
                .map(EmailOutboxEntry::getId)
                .toList();

        int attempted = 0;
        for (Long id : ids) {
            EmailOutboxEntry entry = outbox.findById(id).orElse(null);
            if (entry == null || entry.getStatus() != EmailOutboxStatus.PENDING) {
                continue;
            }
            try {
                String token = extractToken(entry.getPayload());
                String link = switch (entry.getTemplate()) {
                    case VERIFY_EMAIL -> props.baseUrl() + "/verify-email?token=" + token;
                    case RESET_PASSWORD -> props.baseUrl() + "/reset-password?token=" + token;
                };
                String body = "请打开以下链接完成操作：\n" + link + "\n若非本人操作请忽略本邮件。";
                emailSender.send(entry.getToEmail(), entry.getTemplate().subject(), body);
                entry.markSent();
            } catch (Exception ex) {
                log.warn("Email delivery failed for outbox id={} attempt={} type={}",
                        entry.getId(), entry.getAttempts() + 1, ex.getClass().getSimpleName());
                log.debug("Email delivery exception for outbox id={}", entry.getId(), ex);
                entry.markFailed(now);
                if (entry.getStatus() == EmailOutboxStatus.FAILED) {
                    log.error("Email permanently failed for outbox id={} attempts={}",
                            entry.getId(), entry.getAttempts());
                }
            }
            try {
                outbox.save(entry);
            } catch (Exception ex) {
                log.error("Failed to persist outbox id={}", entry.getId(), ex);
            }
            attempted++;
        }
        return attempted;
    }

    private String extractToken(Map<String, Object> payload) {
        String token = payload == null ? null : String.valueOf(payload.get("token"));
        if (token == null || token.isBlank() || "null".equals(token)) {
            throw new IllegalStateException("outbox payload is missing token");
        }
        return token;
    }
}
