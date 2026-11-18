package com.rance.galcommunity.community.web.dto;

import java.time.Instant;

public record TopicListItem(long id, String title, String categorySlug, String categoryName,
                            boolean pinned, boolean locked, int postCount, long viewCount,
                            UserBrief author, Instant createdAt, Instant lastPostAt) {
}
