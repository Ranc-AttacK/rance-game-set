package com.rance.galcommunity.community.repo;

import com.rance.galcommunity.community.domain.TopicTag;
import com.rance.galcommunity.community.domain.TopicTagId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicTagRepository extends JpaRepository<TopicTag, TopicTagId> {

    @Query("select tt.id.tagId from TopicTag tt where tt.id.topicId = :topicId")
    List<Long> findTagIdsByTopicId(@Param("topicId") long topicId);
}
