package com.ebank.accounts.infrastructure.cache;

import com.ebank.accounts.application.dto.AccountDto;
import com.ebank.accounts.application.port.out.AccountCachePort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class RedisAccountCacheAdapter implements AccountCachePort {

    private static final String PREFIX = "account:";
    private static final String ALL_KEY = "account:all";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<AccountDto> findById(Long id) {
        try {
            String json = redis.opsForValue().get(PREFIX + id);
            if (json != null) return Optional.of(objectMapper.readValue(json, AccountDto.class));
        } catch (Exception e) {
            log.warn("Cache miss for account {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void cacheById(AccountDto dto) {
        try {
            redis.opsForValue().set(PREFIX + dto.id(), objectMapper.writeValueAsString(dto), TTL);
        } catch (Exception e) {
            log.warn("Failed to cache account {}: {}", dto.id(), e.getMessage());
        }
    }

    @Override
    public void evictById(Long id) {
        redis.delete(PREFIX + id);
    }

    @Override
    public Optional<List<AccountDto>> findAll() {
        try {
            String json = redis.opsForValue().get(ALL_KEY);
            if (json != null) return Optional.of(objectMapper.readValue(json, new TypeReference<>() {}));
        } catch (Exception e) {
            log.warn("Cache miss for all accounts: {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void cacheAll(List<AccountDto> accounts) {
        try {
            redis.opsForValue().set(ALL_KEY, objectMapper.writeValueAsString(accounts), TTL);
        } catch (Exception e) {
            log.warn("Failed to cache all accounts: {}", e.getMessage());
        }
    }

    @Override
    public void evictAll() {
        redis.delete(ALL_KEY);
    }
}
