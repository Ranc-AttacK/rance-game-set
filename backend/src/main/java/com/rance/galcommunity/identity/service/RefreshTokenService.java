package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.RefreshToken;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.RefreshTokenRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.identity.security.AppProperties;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import com.rance.galcommunity.shared.util.Hashing;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokens;
    private final UserAccountRepository users;
    private final AppProperties props;

    public RefreshTokenService(RefreshTokenRepository refreshTokens,
                               UserAccountRepository users,
                               AppProperties props) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.props = props;
    }

    @Transactional
    public IssuedRefresh issue(UserAccount user, String familyId) {
        String raw = Hashing.randomToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setFamilyId(familyId);
        token.setTokenHash(Hashing.sha256(raw));
        token.setExpiresAt(Instant.now().plus(props.jwt().refreshTtl()));
        refreshTokens.save(token);
        return new IssuedRefresh(raw, token);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public RotatedToken rotate(String rawRefreshToken) {
        RefreshToken token = refreshTokens.findByTokenHashForUpdate(Hashing.sha256(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_REUSED, "登录已失效，请重新登录"));

        if (token.getRevokedAt() != null || token.getReplacedBy() != null) {
            refreshTokens.revokeFamily(token.getFamilyId(), Instant.now());
            throw new BusinessException(ErrorCode.TOKEN_REUSED, "登录已失效，请重新登录");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.TOKEN_REUSED, "登录已失效，请重新登录");
        }

        UserAccount user = users.findById(token.getUserId())
                .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            refreshTokens.revokeFamily(token.getFamilyId(), Instant.now());
            throw new BusinessException(ErrorCode.TOKEN_REUSED, "登录已失效，请重新登录");
        }

        IssuedRefresh issued = issue(user, token.getFamilyId());
        token.setRevokedAt(Instant.now());
        token.setReplacedBy(issued.token().getId());
        return new RotatedToken(user, issued.rawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(Hashing.sha256(rawRefreshToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void revokeAllForUser(long userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    public record IssuedRefresh(String rawToken, RefreshToken token) {
    }

    public record RotatedToken(UserAccount user, String rawToken) {
    }
}
