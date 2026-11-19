package com.rance.galcommunity.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "community_topic_follows")
@Getter
@Setter
@NoArgsConstructor
public class TopicFollow {

    public static final String WATCHING = "WATCHING";

    @EmbeddedId
    private TopicFollowId id;

    @Column(nullable = false, length = 16)
    private String state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TopicFollow(long topicId, long userId, String state) {
        this.id = new TopicFollowId(topicId, userId);
        this.state = state;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
