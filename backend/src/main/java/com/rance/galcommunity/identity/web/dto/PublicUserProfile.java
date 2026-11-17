package com.rance.galcommunity.identity.web.dto;

import com.rance.galcommunity.identity.domain.UserAccount;

public record PublicUserProfile(
        long id,
        String nickname,
        String avatarUrl,
        String bio,
        java.time.Instant createdAt) {

    public static PublicUserProfile from(UserAccount user) {
        return new PublicUserProfile(user.getId(), user.getNickname(),
                user.getAvatarUrl(), user.getBio(), user.getCreatedAt());
    }
}
