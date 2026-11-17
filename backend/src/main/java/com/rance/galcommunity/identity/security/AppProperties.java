package com.rance.galcommunity.identity.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        List<String> allowedOrigins,
        Jwt jwt,
        Cookie cookie,
        Email email) {

    public record Jwt(String privateKey, String publicKey, String issuer,
                      Duration accessTtl, Duration refreshTtl) {
    }

    public record Cookie(boolean secure) {
    }

    public record Email(boolean dispatchEnabled, long pollIntervalMs, String from) {
    }
}
