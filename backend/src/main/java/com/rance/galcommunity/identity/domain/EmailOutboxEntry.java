package com.rance.galcommunity.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_email_outbox")
@Getter
@Setter
@NoArgsConstructor
public class EmailOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_email", nullable = false)
    private String toEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EmailTemplate template;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailOutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = EmailOutboxStatus.PENDING;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = Instant.now();
        }
    }

    public void markSent() {
        status = EmailOutboxStatus.SENT;
        payload = Map.of();
    }

    public void markFailed(Instant now) {
        attempts += 1;
        if (attempts >= 5) {
            status = EmailOutboxStatus.FAILED;
        } else {
            nextAttemptAt = now.plusSeconds((long) Math.pow(2, attempts) * 60);
        }
    }
}
