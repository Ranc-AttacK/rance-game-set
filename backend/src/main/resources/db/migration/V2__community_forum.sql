CREATE TABLE community_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NULL,
    slug VARCHAR(80) NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NULL,
    icon VARCHAR(80) NULL,
    color VARCHAR(16) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    min_tags INT NOT NULL DEFAULT 0,
    max_tags INT NOT NULL DEFAULT 5,
    topic_count BIGINT NOT NULL DEFAULT 0,
    post_count BIGINT NOT NULL DEFAULT 0,
    last_post_id BIGINT NULL,
    last_post_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_slug (slug),
    KEY idx_category_parent (parent_id, sort_order, id),
    KEY idx_category_enabled (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'DISCUSSION',
    title VARCHAR(160) NOT NULL,
    pinned BIT(1) NOT NULL DEFAULT b'0',
    locked BIT(1) NOT NULL DEFAULT b'0',
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    deleted_at DATETIME(6) NULL,
    post_count INT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    last_post_id BIGINT NULL,
    last_post_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_topic_category_list (category_id, visibility, deleted_at, pinned, last_post_at, id),
    KEY idx_topic_latest (last_post_at, id),
    KEY idx_topic_author (author_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    reply_to_post_id BIGINT NULL,
    floor INT NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    deleted_at DATETIME(6) NULL,
    edit_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_topic_floor (topic_id, floor),
    KEY idx_post_topic_page (topic_id, visibility, deleted_at, floor),
    KEY idx_post_author (author_id, created_at),
    KEY idx_post_reply (reply_to_post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    slug VARCHAR(60) NOT NULL,
    color VARCHAR(16) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name),
    UNIQUE KEY uk_tag_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_topic_tags (
    topic_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (topic_id, tag_id),
    KEY idx_topic_tag_tag (tag_id, topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
