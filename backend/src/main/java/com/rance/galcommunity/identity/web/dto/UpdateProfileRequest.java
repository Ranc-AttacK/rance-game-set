package com.rance.galcommunity.identity.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 40) String nickname,
        @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "头像需为 http/https 链接") String avatarUrl,
        @Size(max = 500) String bio) {
}
