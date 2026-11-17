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
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public int processDue(Instant now) {
        List<EmailOutboxEntry> batch = outbox
                .findTop20ByStatusAndNextAttemptAtBeforeOrderByIdAsc(EmailOutboxStatus.PENDING, now);
        for (EmailOutboxEntry entry : batch) {
            try {
                Map<String, Object> payload = entry.getPayload();
                String token = String.valueOf(payload.get("token"));
                String link = switch (entry.getTemplate()) {
                    case VERIFY_EMAIL -> props.baseUrl() + "/verify-email?token=" + token;
                    case RESET_PASSWORD -> props.baseUrl() + "/reset-password?token=" + token;
                };
                String body = "请打开以下链接完成操作：\n" + link + "\n若非本人操作请忽略本邮件。";
                emailSender.send(entry.getToEmail(), entry.getTemplate().subject(), body);
                entry.markSent();
            } catch (Exception ex) {
                log.warn("Email delivery failed for outbox id={} attempt={}", entry.getId(), entry.getAttempts() + 1, ex);
                entry.markFailed(now);
            }
        }
        outbox.saveAll(batch);
        return batch.size();
    }
}
