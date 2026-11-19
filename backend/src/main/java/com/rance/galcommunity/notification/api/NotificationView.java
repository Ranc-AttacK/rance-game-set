package com.rance.galcommunity.notification.api;

import java.time.Instant;

public record NotificationView(long id, String type, long actorId, String actorNickname,
                               String actorAvatarUrl, Long topicId, String topicTitle,
                               Long postId, boolean read, Instant createdAt) {
}
