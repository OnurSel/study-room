package com.studyroom.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Redis-backed fixed-window rate limiting for REST and STOMP handlers.
 *
 * Each call increments a Redis counter and sets its expiration atomically.
 * All application instances therefore share the same counters. Callers must
 * supply a trusted identifier (for example a verified participant UUID), not
 * an arbitrary client-provided value.
 *
 * IMPORTANT: This service does not enforce limits automatically. Controllers,
 * WebSocket handlers and connection interceptors must explicitly call it.
 */
@Service
public class RateLimitService {

    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9:_-]{1,128}");

    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); " +
            "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; " +
            "return count;",
            Long.class);

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    /**
     * Attempts one operation within a fixed window.
     *
     * @param scope a stable category, e.g. "auth" or "room-message"
     * @param subject trusted participant, connection, or room identifier
     * @param limit positive maximum number of operations per window
     * @param window strictly positive window duration
     * @return true if allowed; false if the quota is exceeded
     * @throws org.springframework.dao.DataAccessException if Redis is unavailable
     */
    public boolean allow(String scope, String subject, long limit, Duration window) {
        validateKeyPart(scope, "scope");
        validateKeyPart(subject, "subject");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Objects.requireNonNull(window, "window");
        long ttlMillis = window.toMillis();
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("window must be at least one millisecond");
        }

        String key = "studyroom:ratelimit:" + scope + ":" + subject;
        Long count = redis.execute(INCREMENT_WITH_TTL, List.of(key), Long.toString(ttlMillis));
        if (count == null) {
            throw new IllegalStateException("Redis returned no rate-limit counter");
        }
        return count <= limit;
    }

    private static void validateKeyPart(String value, String field) {
        if (value == null || !SAFE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must contain 1-128 safe characters");
        }
    }
}
