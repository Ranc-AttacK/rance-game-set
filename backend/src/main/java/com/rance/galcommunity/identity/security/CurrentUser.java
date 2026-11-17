package com.rance.galcommunity.identity.security;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

public record CurrentUser(long id, boolean admin) {

    public static CurrentUser of(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new CurrentUser(Long.parseLong(jwt.getSubject()),
                roles != null && roles.contains("ADMIN"));
    }
}
