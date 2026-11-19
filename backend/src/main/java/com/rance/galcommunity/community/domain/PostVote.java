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
@Table(name = "community_post_votes")
@Getter
@Setter
@NoArgsConstructor
public class PostVote {

    @EmbeddedId
    private PostVoteId id;

    @Column(nullable = false)
    private int value;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PostVote(long postId, long userId) {
        this.id = new PostVoteId(postId, userId);
        this.value = 1;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
