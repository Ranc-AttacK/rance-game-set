ALTER TABLE community_topics
    ADD COLUMN follower_count INT NOT NULL DEFAULT 0;

ALTER TABLE community_posts
    ADD COLUMN upvote_count INT NOT NULL DEFAULT 0;

CREATE TABLE community_user_stats (
    user_id BIGINT NOT NULL,
    topic_count INT NOT NULL DEFAULT 0,
    post_count INT NOT NULL DEFAULT 0,
    reputation INT NOT NULL DEFAULT 0,
    last_post_at DATETIME(6) NULL,
    PRIMARY KEY (user_id),
    KEY idx_user_stats_reputation (reputation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_post_votes (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    value INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id, user_id),
    KEY idx_post_votes_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_topic_follows (
    topic_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (topic_id, user_id),
    KEY idx_topic_follows_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    topic_id BIGINT NULL,
    topic_title VARCHAR(160) NULL,
    post_id BIGINT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notification_recipient (recipient_id, read_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
