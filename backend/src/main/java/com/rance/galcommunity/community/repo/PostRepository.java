package com.rance.galcommunity.community.repo;

import com.rance.galcommunity.community.domain.Post;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
            select p from Post p
            where p.topicId = :topicId
              and p.visibility = 'PUBLIC'
              and p.deletedAt is null
            order by p.floor asc
            """)
    Page<Post> findVisiblePage(@Param("topicId") long topicId, Pageable pageable);

    List<Post> findByIdIn(Collection<Long> ids);

    @Query("""
            select count(p) from Post p
            where p.topicId = :topicId and p.visibility = 'PUBLIC' and p.deletedAt is null
            """)
    long countVisible(@Param("topicId") long topicId);

    @Query("""
            select p from Post p
            where p.topicId = :topicId and p.visibility = 'PUBLIC' and p.deletedAt is null
            order by p.floor desc
            """)
    List<Post> findLastVisible(@Param("topicId") long topicId, Pageable pageable);
}
