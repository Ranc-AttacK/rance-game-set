package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.security.AppProperties;
import com.rance.galcommunity.identity.security.JwtKeyProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtKeyProvider keyProvider;
    private final AppProperties props;

    public TokenService(JwtEncoder encoder, JwtKeyProvider keyProvider, AppProperties props) {
        this.encoder = encoder;
        this.keyProvider = keyProvider;
        this.props = props;
    }

    public IssuedToken issue(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.jwt().accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of(user.getRole().name()))
                .claim("nickname", user.getNickname())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyProvider.kid()).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, props.jwt().accessTtl().toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
