package com.rance.galcommunity.identity.web.dto;

public record LoginResponse(String accessToken, long expiresIn, UserProfileResponse user) {
}
