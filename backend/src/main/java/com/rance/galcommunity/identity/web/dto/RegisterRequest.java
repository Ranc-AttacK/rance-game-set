package com.rance.galcommunity.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(min = 2, max = 40) String nickname,
        @NotBlank String password) {

    public RegisterRequest {
        email = email == null ? null : email.trim();
        nickname = nickname == null ? null : nickname.trim();
    }
}
