package com.rance.galcommunity.identity.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        List<String> allowedOrigins,
        List<String> trustedProxies,
        String bootstrapAdminEmail,
        Jwt jwt,
        Cookie cookie,
        Email email) {

    public record Jwt(String privateKey, String publicKey,
                      String privateKeyFile, String publicKeyFile,
                      String issuer, Duration accessTtl, Duration refreshTtl,
                      boolean requireConfiguredKeys) {
    }

    public record Cookie(boolean secure) {
    }

    public record Email(boolean dispatchEnabled, long pollIntervalMs, String from) {
    }
}
