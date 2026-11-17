package com.rance.galcommunity.support;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProbeSecureController {

    @GetMapping("/test/secure/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("sub", jwt.getSubject(), "roles", jwt.getClaimAsStringList("roles"));
    }

    @GetMapping("/api/v1/admin/probe")
    public Map<String, Object> admin() {
        return Map.of("ok", true);
    }
}
