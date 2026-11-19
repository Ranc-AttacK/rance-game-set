package com.rance.galcommunity.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "community_user_stats")
@Getter
@Setter
@NoArgsConstructor
public class UserStat {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "topic_count", nullable = false)
    private int topicCount;

    @Column(name = "post_count", nullable = false)
    private int postCount;

    @Column(nullable = false)
    private int reputation;

    @Column(name = "last_post_at")
    private Instant lastPostAt;
}
