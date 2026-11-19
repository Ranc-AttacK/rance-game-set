package com.rance.galcommunity.community.repo;

import com.rance.galcommunity.community.domain.UserStat;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserStatRepository extends JpaRepository<UserStat, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO community_user_stats (user_id, topic_count, post_count, reputation, last_post_at)
            VALUES (:userId, 1, 1, 0, :at)
            ON DUPLICATE KEY UPDATE topic_count = topic_count + 1,
                                    post_count = post_count + 1,
                                    last_post_at = VALUES(last_post_at)
            """, nativeQuery = true)
    void recordTopic(@Param("userId") long userId, @Param("at") Instant at);

    @Modifying
    @Query(value = """
            INSERT INTO community_user_stats (user_id, topic_count, post_count, reputation, last_post_at)
            VALUES (:userId, 0, 1, 0, :at)
            ON DUPLICATE KEY UPDATE post_count = post_count + 1,
                                    last_post_at = VALUES(last_post_at)
            """, nativeQuery = true)
    void recordPost(@Param("userId") long userId, @Param("at") Instant at);

    @Modifying
    @Query(value = """
            UPDATE community_user_stats SET topic_count = GREATEST(topic_count - 1, 0)
            WHERE user_id = :userId
            """, nativeQuery = true)
    void decrementTopics(@Param("userId") long userId);

    @Modifying
    @Query(value = """
            UPDATE community_user_stats SET post_count = GREATEST(post_count - 1, 0)
            WHERE user_id = :userId
            """, nativeQuery = true)
    void decrementPosts(@Param("userId") long userId);

    @Modifying
    @Query(value = """
            INSERT INTO community_user_stats (user_id, topic_count, post_count, reputation)
            VALUES (:userId, 0, 0, :delta)
            ON DUPLICATE KEY UPDATE reputation = reputation + :delta
            """, nativeQuery = true)
    void adjustReputation(@Param("userId") long userId, @Param("delta") int delta);
}
