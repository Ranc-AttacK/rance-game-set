package com.rance.galcommunity.notification.web;

import com.rance.galcommunity.notification.api.NotificationApi;
import com.rance.galcommunity.notification.api.NotificationView;
import com.rance.galcommunity.shared.api.ApiResponse;
import com.rance.galcommunity.shared.api.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationApi notifications;

    public NotificationController(NotificationApi notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationView>> page(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notifications.page(userId(jwt), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(new UnreadCount(notifications.unreadCount(userId(jwt))));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        notifications.markRead(userId(jwt), id);
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notifications.markAllRead(userId(jwt));
        return ApiResponse.ok();
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record UnreadCount(long count) {
    }
}
