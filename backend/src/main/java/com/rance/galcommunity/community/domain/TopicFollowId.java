package com.rance.galcommunity.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record TopicFollowId(@Column(name = "topic_id") Long topicId,
                            @Column(name = "user_id") Long userId) implements Serializable {
}
