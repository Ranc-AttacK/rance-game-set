package com.rance.galcommunity.identity.web.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(@NotBlank String token) {
}
