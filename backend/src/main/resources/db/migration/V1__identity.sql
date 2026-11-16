CREATE TABLE user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(40) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    bio VARCHAR(500) NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    email_verified BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    replaced_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_user (user_id),
    KEY idx_refresh_family (family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_email_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_token (token_hash),
    KEY idx_email_verification_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_password_resets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_token (token_hash),
    KEY idx_password_reset_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_email_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    to_email VARCHAR(255) NOT NULL,
    template VARCHAR(40) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_email_outbox_due (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
