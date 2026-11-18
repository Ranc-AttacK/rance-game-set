package com.rance.galcommunity.community.repo;

import com.rance.galcommunity.community.domain.Category;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlugAndEnabledTrue(String slug);

    List<Category> findByEnabledTrueOrderBySortOrderAscIdAsc();

    @Modifying
    @Query("""
            update Category c set c.topicCount = c.topicCount + 1, c.postCount = c.postCount + 1
            where c.id = :id
            """)
    void incrementAfterTopic(@Param("id") long id);

    @Modifying
    @Query("update Category c set c.postCount = c.postCount + 1 where c.id = :id")
    void incrementPost(@Param("id") long id);

    @Modifying
    @Query("""
            update Category c set c.lastPostId = :postId, c.lastPostAt = :at where c.id = :id
            """)
    void updateLastPost(@Param("id") long id, @Param("postId") long postId,
                        @Param("at") Instant at);
}
