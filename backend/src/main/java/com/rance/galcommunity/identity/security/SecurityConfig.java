package com.rance.galcommunity.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY permit-all security scaffold for Task 1.
 *
 * <p>This configuration intentionally permits every request so the scaffold and
 * health endpoint can be exercised before authentication exists. It is replaced by
 * the real RS256 JWT security configuration in Task 7 and MUST NOT ship in that state.
 */
@Deprecated(forRemoval = true)
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain temporaryPermitAll(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
