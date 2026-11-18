package com.rance.galcommunity.community.web.dto;

import java.util.List;

public record CategoryNode(long id, Long parentId, String slug, String name, String description,
                           String icon, String color, int minTags, int maxTags,
                           long topicCount, long postCount,
                           LastPostView lastPost, List<CategoryNode> children) {

    public CategoryNode {
        children = List.copyOf(children);
    }
}
