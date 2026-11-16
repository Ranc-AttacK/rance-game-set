package com.rance.galcommunity.shared.ratelimit;

import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Fixed-window rate limiter.
 *
 * <p>Uses one Lua script so INCR + PEXPIRE are atomic. Fail-closed: if Redis is
 * unavailable, throws {@link BusinessException}(UNAVAILABLE) so auth flows return
 * 503 rather than silently disabling brute-force protection.
 */
@Service
public class RateLimitService {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquire(String key, int limit, Duration window) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
        if (limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("limit and window must be positive");
        }
        try {
            Long count = redis.execute(SCRIPT, List.of(key), String.valueOf(window.toMillis()));
            return count != null && count <= limit;
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "限流服务暂不可用");
        }
    }
}
