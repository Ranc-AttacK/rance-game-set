package com.rance.galcommunity.community.repo;

import com.rance.galcommunity.community.domain.Topic;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Topic t where t.id = :id")
    Optional<Topic> findByIdForUpdate(@Param("id") long id);

    @Query("""
            select t from Topic t
            where t.categoryId = :categoryId
              and t.visibility = 'PUBLIC'
              and t.deletedAt is null
              and (:cursorAt is null
                   or (:cursorPinned = true and t.pinned = false)
                   or (t.pinned = :cursorPinned
                       and (t.lastPostAt < :cursorAt
                            or (t.lastPostAt = :cursorAt and t.id < :cursorId))))
            order by t.pinned desc, t.lastPostAt desc, t.id desc
            """)
    List<Topic> findCategoryPage(@Param("categoryId") long categoryId,
                                 @Param("cursorPinned") boolean cursorPinned,
                                 @Param("cursorAt") Instant cursorAt,
                                 @Param("cursorId") long cursorId,
                                 Pageable pageable);

    @Query("""
            select t from Topic t
            where t.visibility = 'PUBLIC'
              and t.deletedAt is null
              and (:cursorAt is null
                   or t.lastPostAt < :cursorAt
                   or (t.lastPostAt = :cursorAt and t.id < :cursorId))
            order by t.lastPostAt desc, t.id desc
            """)
    List<Topic> findLatestPage(@Param("cursorAt") Instant cursorAt,
                               @Param("cursorId") long cursorId,
                               Pageable pageable);

    @Modifying
    @Query("update Topic t set t.viewCount = t.viewCount + 1 where t.id = :id")
    void incrementView(@Param("id") long id);

    @Query("""
            select count(t) from Topic t
            where t.categoryId = :categoryId and t.visibility = 'PUBLIC' and t.deletedAt is null
            """)
    long countVisibleInCategory(@Param("categoryId") long categoryId);

    @Query("""
            select coalesce(sum(t.postCount), 0) from Topic t
            where t.categoryId = :categoryId and t.visibility = 'PUBLIC' and t.deletedAt is null
            """)
    long sumVisiblePostCount(@Param("categoryId") long categoryId);

    @Query("""
            select t from Topic t
            where t.categoryId = :categoryId and t.visibility = 'PUBLIC' and t.deletedAt is null
              and t.lastPostAt is not null
            order by t.lastPostAt desc, t.id desc
            """)
    List<Topic> findLastVisibleInCategory(@Param("categoryId") long categoryId, Pageable pageable);
}
