package com.rance.galcommunity.community.web.dto;

import java.time.Instant;

public record PostView(long id, int floor, String content, UserBrief author,
                       Long replyToPostId, Instant createdAt, int upvoteCount, boolean upvoted) {
}
