package com.rance.galcommunity.community.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(@NotBlank @Size(max = 20000) String content) {
}
