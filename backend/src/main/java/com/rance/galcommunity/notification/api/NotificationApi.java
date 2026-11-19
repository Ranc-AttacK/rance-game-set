package com.rance.galcommunity.notification.api;

import com.rance.galcommunity.shared.api.PageResponse;

/**
 * In-app notification contract exposed as {@code notification :: api}.
 * Implementations must never notify a user about their own action.
 */
public interface NotificationApi {

    void reply(long recipientId, long actorId, long topicId, String topicTitle, long postId);

    void quote(long recipientId, long actorId, long topicId, String topicTitle, long postId);

    void followReply(long recipientId, long actorId, long topicId, String topicTitle, long postId);

    void upvote(long recipientId, long actorId, long topicId, String topicTitle, long postId);

    PageResponse<NotificationView> page(long userId, int page, int size);

    long unreadCount(long userId);

    void markRead(long userId, long notificationId);

    void markAllRead(long userId);
}
