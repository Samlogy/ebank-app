package com.ebank.auth.infrastructure.redis;

import com.ebank.auth.application.port.out.RefreshTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class RedisRefreshTokenAdapter implements RefreshTokenPort {

    private static final String PREFIX = "refresh:";
    private final StringRedisTemplate redis;

    @Override
    public void store(String email, String token, Duration ttl) {
        redis.opsForValue().set(PREFIX + email, token, ttl);
    }

    @Override
    public Optional<String> get(String email) {
        return Optional.ofNullable(redis.opsForValue().get(PREFIX + email));
    }

    @Override
    public void revoke(String email) {
        redis.delete(PREFIX + email);
    }
}
