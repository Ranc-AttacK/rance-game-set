package com.rance.galcommunity.community.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "community_topic_tags")
@Getter
@Setter
@NoArgsConstructor
public class TopicTag {

    @EmbeddedId
    private TopicTagId id;

    public TopicTag(long topicId, long tagId) {
        this.id = new TopicTagId(topicId, tagId);
    }
}
