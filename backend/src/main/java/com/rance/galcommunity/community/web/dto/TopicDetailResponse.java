package com.rance.galcommunity.community.web.dto;

import com.rance.galcommunity.shared.api.PageResponse;
import java.time.Instant;
import java.util.List;

public record TopicDetailResponse(long id, String title, String categorySlug, String categoryName,
                                  boolean pinned, boolean locked, long viewCount,
                                  UserBrief author, Instant createdAt,
                                  List<TagView> tags, PageResponse<PostView> posts) {

    public TopicDetailResponse {
        tags = List.copyOf(tags);
    }
}
