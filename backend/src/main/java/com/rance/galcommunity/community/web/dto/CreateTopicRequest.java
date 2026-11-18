package com.rance.galcommunity.community.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateTopicRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(min = 4, max = 160) String title,
        @NotBlank @Size(max = 20000) String content,
        @Size(max = 10) List<@NotBlank @Size(max = 40) String> tags) {
}
