package com.rance.galcommunity.identity.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

    @Bean
    JwtKeyProvider jwtKeyProvider(AppProperties props) {
        return new JwtKeyProvider(props.jwt());
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyProvider keyProvider) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keyProvider.rsaKey())));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyProvider keyProvider, AppProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyProvider.publicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.jwt().issuer()));
        return decoder;
    }
}
