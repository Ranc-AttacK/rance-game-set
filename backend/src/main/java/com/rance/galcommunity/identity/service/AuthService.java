package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.EmailTemplate;
import com.rance.galcommunity.identity.domain.EmailVerification;
import com.rance.galcommunity.identity.domain.PasswordReset;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.EmailVerificationRepository;
import com.rance.galcommunity.identity.repo.PasswordResetRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.identity.web.dto.UserProfileResponse;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import com.rance.galcommunity.shared.ratelimit.RateLimitService;
import com.rance.galcommunity.shared.security.PasswordPolicy;
import com.rance.galcommunity.shared.util.Hashing;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final EmailVerificationRepository verifications;
    private final PasswordResetRepository resets;
    private final EmailOutboxService emailOutbox;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimit;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;
    private final String dummyPasswordHash;

    public AuthService(UserAccountRepository users,
                       EmailVerificationRepository verifications,
                       PasswordResetRepository resets,
                       EmailOutboxService emailOutbox,
                       PasswordEncoder passwordEncoder,
                       RateLimitService rateLimit,
                       RefreshTokenService refreshTokenService,
                       TokenService tokenService) {
        this.users = users;
        this.verifications = verifications;
        this.resets = resets;
        this.emailOutbox = emailOutbox;
        this.passwordEncoder = passwordEncoder;
        this.rateLimit = rateLimit;
        this.refreshTokenService = refreshTokenService;
        this.tokenService = tokenService;
        this.dummyPasswordHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public UserProfileResponse register(String email, String nickname, String password, String ip) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!rateLimit.tryAcquire("rl:register:ip:" + ip, 20, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "注册过于频繁，请稍后再试");
        }
        if (!rateLimit.tryAcquire("rl:register:" + ip + ":" + Hashing.sha256(normalized),
                5, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "注册过于频繁，请稍后再试");
        }
        PasswordPolicy.validate(password);
        if (users.existsByEmail(normalized)) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已注册");
        }

        UserAccount user = new UserAccount();
        user.setEmail(normalized);
        user.setNickname(nickname.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(false);
        users.save(user);

        issueVerification(user);
        return UserProfileResponse.self(user);
    }

    @Transactional
    public LoginResult login(String email, String password, String ip) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!rateLimit.tryAcquire("rl:login:ip:" + ip, 50, Duration.ofMinutes(5))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "登录尝试过于频繁");
        }
        if (!rateLimit.tryAcquire("rl:login:" + ip + ":" + Hashing.sha256(normalized),
                10, Duration.ofMinutes(5))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "登录尝试过于频繁");
        }

        UserAccount user = users.findByEmail(normalized)
                .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
                .orElse(null);
        String passwordHash = user != null ? user.getPasswordHash() : dummyPasswordHash;
        boolean matches = passwordEncoder.matches(password, passwordHash);
        if (user == null || !matches) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "邮箱或密码错误");
        }

        TokenService.IssuedToken access = tokenService.issue(user);
        String refreshToken = refreshTokenService.issue(user,
                java.util.UUID.randomUUID().toString()).rawToken();
        return new LoginResult(access, refreshToken, UserProfileResponse.self(user));
    }

    public LoginResult refresh(String rawRefreshToken) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(rawRefreshToken);
        TokenService.IssuedToken access = tokenService.issue(rotated.user());
        return new LoginResult(access, rotated.rawToken(), UserProfileResponse.self(rotated.user()));
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.logout(rawRefreshToken);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerification verification = verifications.findByTokenHash(Hashing.sha256(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE, "验证链接无效"));
        if (verification.getUsedAt() != null || verification.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "验证链接已失效");
        }
        UserAccount user = users.findById(verification.getUserId())
                .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        user.setEmailVerified(true);
        verification.setUsedAt(Instant.now());
    }

    @Transactional
    public void resendVerification(long userId) {
        UserAccount user = users.findById(userId)
                .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "邮箱已验证");
        }
        if (!rateLimit.tryAcquire("rl:resend:" + userId, 3, Duration.ofMinutes(10))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "重发过于频繁");
        }
        issueVerification(user);
    }

    @Transactional
    public void forgotPassword(String email, String ip) {
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!rateLimit.tryAcquire("rl:forgot:ip:" + ip, 20, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "请求过于频繁");
        }
        if (!rateLimit.tryAcquire("rl:forgot:" + ip + ":" + Hashing.sha256(normalized),
                5, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "请求过于频繁");
        }
        if (!rateLimit.tryAcquire("rl:forgot:user:" + Hashing.sha256(normalized),
                5, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "请求过于频繁");
        }
        users.findByEmail(normalized)
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .flatMap(user -> users.findByIdForUpdate(user.getId()))
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .ifPresent(user -> {
                    resets.expireActive(user.getId(), Instant.now());
                    String rawToken = Hashing.randomToken();
                    PasswordReset reset = new PasswordReset();
                    reset.setUserId(user.getId());
                    reset.setTokenHash(Hashing.sha256(rawToken));
                    reset.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
                    resets.save(reset);
                    emailOutbox.enqueue(user.getEmail(), EmailTemplate.RESET_PASSWORD,
                            Map.of("token", rawToken));
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordPolicy.validate(newPassword);
        Long userId = resets.findUserIdByTokenHash(Hashing.sha256(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE, "重置链接无效"));
        UserAccount user = users.findByIdForUpdate(userId)
                .filter(candidateUser -> candidateUser.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE, "重置链接已失效"));
        PasswordReset reset = resets.findByTokenHashForUpdate(Hashing.sha256(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE, "重置链接已失效"));
        if (reset.getUsedAt() != null || reset.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "重置链接已失效");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        reset.setUsedAt(Instant.now());
        resets.expireActive(user.getId(), Instant.now());
        refreshTokenService.revokeAllForUser(user.getId());
    }

    public record LoginResult(TokenService.IssuedToken access, String refreshToken,
                              UserProfileResponse user) {
    }

    private void issueVerification(UserAccount user) {
        verifications.expireActive(user.getId(), Instant.now());
        String rawToken = Hashing.randomToken();
        EmailVerification verification = new EmailVerification();
        verification.setUserId(user.getId());
        verification.setTokenHash(Hashing.sha256(rawToken));
        verification.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        verifications.save(verification);
        emailOutbox.enqueue(user.getEmail(), EmailTemplate.VERIFY_EMAIL, Map.of("token", rawToken));
    }
}
