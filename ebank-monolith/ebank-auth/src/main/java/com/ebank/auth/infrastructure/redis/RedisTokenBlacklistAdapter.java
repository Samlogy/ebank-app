package com.ebank.auth.infrastructure.redis;

import com.ebank.auth.application.port.out.TokenBlacklistPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final String PREFIX = "blacklist:";
    private final StringRedisTemplate redis;

    @Override
    public void blacklist(String token, Duration ttl) {
        redis.opsForValue().set(PREFIX + token, "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + token));
    }
}
