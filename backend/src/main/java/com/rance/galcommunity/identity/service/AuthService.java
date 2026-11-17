package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.EmailTemplate;
import com.rance.galcommunity.identity.domain.EmailVerification;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.EmailVerificationRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final EmailVerificationRepository verifications;
    private final EmailOutboxService emailOutbox;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimit;

    public AuthService(UserAccountRepository users,
                       EmailVerificationRepository verifications,
                       EmailOutboxService emailOutbox,
                       PasswordEncoder passwordEncoder,
                       RateLimitService rateLimit) {
        this.users = users;
        this.verifications = verifications;
        this.emailOutbox = emailOutbox;
        this.passwordEncoder = passwordEncoder;
        this.rateLimit = rateLimit;
    }

    @Transactional
    public UserProfileResponse register(String email, String nickname, String password, String ip) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        PasswordPolicy.validate(password);
        if (!rateLimit.tryAcquire("rl:register:" + ip + ":" + normalized, 5, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "注册过于频繁，请稍后再试");
        }
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

    private void issueVerification(UserAccount user) {
        String rawToken = Hashing.randomToken();
        EmailVerification verification = new EmailVerification();
        verification.setUserId(user.getId());
        verification.setTokenHash(Hashing.sha256(rawToken));
        verification.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        verifications.save(verification);
        emailOutbox.enqueue(user.getEmail(), EmailTemplate.VERIFY_EMAIL, Map.of("token", rawToken));
    }
}
