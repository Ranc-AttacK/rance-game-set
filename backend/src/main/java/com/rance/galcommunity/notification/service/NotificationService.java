package com.rance.galcommunity.notification.service;

import com.rance.galcommunity.identity.api.IdentityApi;
import com.rance.galcommunity.identity.api.UserSummary;
import com.rance.galcommunity.notification.api.NotificationApi;
import com.rance.galcommunity.notification.api.NotificationView;
import com.rance.galcommunity.notification.domain.Notification;
import com.rance.galcommunity.notification.domain.NotificationType;
import com.rance.galcommunity.notification.repo.NotificationRepository;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import com.rance.galcommunity.shared.api.PageResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService implements NotificationApi {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int RETENTION_DAYS = 30;

    private final NotificationRepository notifications;
    private final IdentityApi identity;

    public NotificationService(NotificationRepository notifications, IdentityApi identity) {
        this.notifications = notifications;
        this.identity = identity;
    }

    @Override
    public void reply(long recipientId, long actorId, long topicId, String topicTitle, long postId) {
        create(recipientId, actorId, NotificationType.POST_REPLY, topicId, topicTitle, postId);
    }

    @Override
    public void quote(long recipientId, long actorId, long topicId, String topicTitle, long postId) {
        create(recipientId, actorId, NotificationType.POST_QUOTE, topicId, topicTitle, postId);
    }

    @Override
    public void followReply(long recipientId, long actorId, long topicId, String topicTitle,
                            long postId) {
        create(recipientId, actorId, NotificationType.TOPIC_FOLLOW_REPLY, topicId, topicTitle, postId);
    }

    @Override
    public void upvote(long recipientId, long actorId, long topicId, String topicTitle, long postId) {
        create(recipientId, actorId, NotificationType.POST_UPVOTE, topicId, topicTitle, postId);
    }

    @Transactional
    protected void create(long recipientId, long actorId, NotificationType type, long topicId,
                          String topicTitle, long postId) {
        if (recipientId == actorId) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setActorId(actorId);
        notification.setType(type);
        notification.setTopicId(topicId);
        notification.setTopicTitle(topicTitle);
        notification.setPostId(postId);
        notifications.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationView> page(long userId, int page, int size) {
        int pageNumber = Math.max(1, page);
        int pageSize = Math.min(Math.max(size, 1), 50);
        Page<Notification> result = notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(
                userId, PageRequest.of(pageNumber - 1, pageSize));
        Set<Long> actorIds = result.getContent().stream()
                .map(Notification::getActorId)
                .collect(Collectors.toSet());
        Map<Long, UserSummary> actors = actorIds.isEmpty() ? Map.of() : identity.findUsers(actorIds);
        List<NotificationView> views = result.getContent().stream()
                .map(notification -> {
                    UserSummary actor = actors.get(notification.getActorId());
                    return new NotificationView(notification.getId(),
                            notification.getType().name(), notification.getActorId(),
                            actor != null ? actor.nickname() : "已注销用户",
                            actor != null ? actor.avatarUrl() : null,
                            notification.getTopicId(), notification.getTopicTitle(),
                            notification.getPostId(), notification.getReadAt() != null,
                            notification.getCreatedAt());
                })
                .toList();
        return new PageResponse<>(views, result.getTotalElements(), pageNumber, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(long userId) {
        return notifications.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Override
    @Transactional
    public void markRead(long userId, long notificationId) {
        Notification notification = notifications.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "通知不存在"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
    }

    @Override
    @Transactional
    public void markAllRead(long userId) {
        notifications.markAllRead(userId, Instant.now());
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = notifications.deleteOlderThan(
                Instant.now().minus(Duration.ofDays(RETENTION_DAYS)));
        if (deleted > 0) {
            log.info("Purged {} notifications older than {} days", deleted, RETENTION_DAYS);
        }
    }
}
