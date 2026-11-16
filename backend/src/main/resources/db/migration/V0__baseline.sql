-- Baseline schema required by Spring Modulith's JPA event publication registry.
-- Mirrors the DDL Hibernate generates for
-- org.springframework.modulith.events.jpa.JpaEventPublication, so that
-- spring.jpa.hibernate.ddl-auto=validate succeeds before any identity tables exist.
CREATE TABLE event_publication (
    completion_date  DATETIME(6),
    publication_date DATETIME(6),
    id               BINARY(16) NOT NULL,
    event_type       VARCHAR(255),
    listener_id      VARCHAR(255),
    serialized_event VARCHAR(255),
    PRIMARY KEY (id),
    KEY idx_event_publication_completion_date (completion_date)
) ENGINE = InnoDB;
