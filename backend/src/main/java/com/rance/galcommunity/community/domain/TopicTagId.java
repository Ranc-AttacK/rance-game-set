package com.rance.galcommunity.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record TopicTagId(@Column(name = "topic_id") Long topicId,
                         @Column(name = "tag_id") Long tagId) implements Serializable {
}
