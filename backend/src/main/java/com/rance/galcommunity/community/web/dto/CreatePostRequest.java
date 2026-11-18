package com.rance.galcommunity.community.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotBlank @Size(max = 20000) String content,
        Long replyToPostId) {
}
