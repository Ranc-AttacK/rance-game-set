package com.rance.galcommunity.community.web.dto;

import java.time.Instant;

public record LastPostView(long postId, long topicId, String topicTitle,
                           long authorId, String authorNickname, Instant at) {
}
