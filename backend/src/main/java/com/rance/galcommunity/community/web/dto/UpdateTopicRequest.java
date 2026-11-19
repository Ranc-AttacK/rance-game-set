package com.rance.galcommunity.community.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTopicRequest(@NotBlank @Size(min = 4, max = 160) String title) {
}
