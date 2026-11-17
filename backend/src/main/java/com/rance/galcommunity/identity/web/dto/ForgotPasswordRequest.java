package com.rance.galcommunity.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @Email @NotBlank @Size(max = 255) String email) {

    public ForgotPasswordRequest {
        email = email == null ? null : email.trim();
    }
}
