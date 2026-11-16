package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.ratelimit.RateLimitService;
import com.rance.galcommunity.shared.security.PasswordPolicy;
import com.rance.galcommunity.shared.util.Hashing;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RateLimitIT extends IntegrationTestBase {

    @Autowired
    RateLimitService rateLimitService;

    @Autowired
    org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Test
    void allowsUpToLimitThenBlocks() {
        String key = "test:" + UUID.randomUUID();
        assertThat(rateLimitService.tryAcquire(key, 2, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimitService.tryAcquire(key, 2, Duration.ofMinutes(1))).isTrue();
        assertThat(redisTemplate.getExpire(key)).isGreaterThan(0);
        assertThat(rateLimitService.tryAcquire(key, 2, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void passwordPolicyRejectsWeakPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short1"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("onlyletters"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("12345678"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("密".repeat(30) + "1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void passwordPolicyAcceptsLettersAndDigits() {
        PasswordPolicy.validate("secret123");
        PasswordPolicy.validate("密码12345678");
    }

    @Test
    void hashingIsStableAndTokensAreUnique() {
        assertThat(Hashing.sha256("abc")).isEqualTo(Hashing.sha256("abc"));
        assertThat(Hashing.sha256("abc")).hasSize(64);
        assertThat(Hashing.randomToken()).isNotEqualTo(Hashing.randomToken());
    }
}
