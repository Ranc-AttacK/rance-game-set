package com.rance.galcommunity.identity.web.dto;

import com.rance.galcommunity.identity.domain.UserAccount;

public record UserProfileResponse(
        long id,
        String email,
        String nickname,
        String avatarUrl,
        String bio,
        boolean emailVerified,
        String role,
        java.time.Instant createdAt) {

    public static UserProfileResponse self(UserAccount user) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getNickname(),
                user.getAvatarUrl(), user.getBio(), user.isEmailVerified(),
                user.getRole().name(), user.getCreatedAt());
    }
}
