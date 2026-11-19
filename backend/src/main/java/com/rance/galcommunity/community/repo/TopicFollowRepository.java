package com.rance.galcommunity.community.repo;

import com.rance.galcommunity.community.domain.TopicFollow;
import com.rance.galcommunity.community.domain.TopicFollowId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicFollowRepository extends JpaRepository<TopicFollow, TopicFollowId> {

    List<TopicFollow> findByIdTopicIdAndState(long topicId, String state);

    long countByIdTopicId(long topicId);
}
