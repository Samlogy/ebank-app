package com.ebank.accounts.cache;

import com.ebank.accounts.api.Account;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@Profile("!test")
public class AccountCacheService {

    private static final String ACCOUNT_BY_ID_KEY_PREFIX = "account:by-id:";
    private static final String ALL_ACCOUNTS_KEY = "accounts:all";
    private static final Duration ACCOUNT_TTL = Duration.ofMinutes(10);
    private static final Duration ALL_ACCOUNTS_TTL = Duration.ofMinutes(5);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public AccountCacheService(ReactiveStringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               MeterRegistry registry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheHitCounter = Counter.builder("accounts.cache")
                .tag("result", "hit")
                .description("Number of Redis cache hits for accounts")
                .register(registry);
        this.cacheMissCounter = Counter.builder("accounts.cache")
                .tag("result", "miss")
                .description("Number of Redis cache misses for accounts")
                .register(registry);
    }

    /**
     * Get single account from cache by ID.
     * Returns Mono.empty() if not found in cache.
     */
    public Mono<Account> getCachedById(Long id) {
        String key = ACCOUNT_BY_ID_KEY_PREFIX + id;
        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(value -> {
                    try {
                        Account account = objectMapper.readValue(value, Account.class);
                        cacheHitCounter.increment();
                        log.debug("Cache hit for account id: {}", id);
                        return Mono.just(account);
                    } catch (Exception e) {
                        log.error("Failed to deserialize account from cache for id: {}", id, e);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.fromRunnable(cacheMissCounter::increment))
                .doOnError(e -> log.error("Cache retrieval error for account id: {}", id, e))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Get all accounts from cache.
     * Returns Mono.empty() if not found in cache.
     */
    public Mono<List<Account>> getCachedAll() {
        return redisTemplate.opsForValue()
                .get(ALL_ACCOUNTS_KEY)
                .flatMap(value -> {
                    try {
                        Account[] accounts = objectMapper.readValue(value, Account[].class);
                        cacheHitCounter.increment();
                        log.debug("Cache hit for all accounts");
                        return Mono.just(Arrays.asList(accounts));
                    } catch (Exception e) {
                        log.error("Failed to deserialize all accounts from cache", e);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.fromRunnable(cacheMissCounter::increment))
                .doOnError(e -> log.error("Cache retrieval error for all accounts", e))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Put single account in cache with TTL.
     */
    public Mono<Boolean> putById(Account account) {
        if (account == null || account.getId() == null) {
            return Mono.just(false);
        }
        String key = ACCOUNT_BY_ID_KEY_PREFIX + account.getId();
        try {
            String json = objectMapper.writeValueAsString(account);
            return redisTemplate.opsForValue()
                    .set(key, json, ACCOUNT_TTL)
                    .doOnNext(success -> log.debug("Cache set for account id: {}", account.getId()))
                    .doOnError(e -> log.error("Failed to cache account id: {}", account.getId(), e))
                    .onErrorResume(e -> Mono.just(false));
        } catch (Exception e) {
            log.error("Failed to serialize account for caching id: {}", account.getId(), e);
            return Mono.just(false);
        }
    }

    /**
     * Put all accounts in cache with TTL.
     */
    public Mono<Boolean> putAll(List<Account> accounts) {
        if (accounts == null) {
            return Mono.just(false);
        }
        try {
            String json = objectMapper.writeValueAsString(accounts);
            return redisTemplate.opsForValue()
                    .set(ALL_ACCOUNTS_KEY, json, ALL_ACCOUNTS_TTL)
                    .doOnNext(success -> log.debug("Cache set for all accounts"))
                    .doOnError(e -> log.error("Failed to cache all accounts", e))
                    .onErrorResume(e -> Mono.just(false));
        } catch (Exception e) {
            log.error("Failed to serialize all accounts for caching", e);
            return Mono.just(false);
        }
    }

    /**
     * Evict single account from cache by ID.
     */
    public Mono<Boolean> evictById(Long id) {
        String key = ACCOUNT_BY_ID_KEY_PREFIX + id;
        return redisTemplate.delete(key)
                .map(deletedCount -> {
                    if (deletedCount > 0) {
                        log.debug("Cache evicted for account id: {}", id);
                    }
                    return deletedCount > 0;
                })
                .doOnError(e -> log.error("Failed to evict account cache id: {}", id, e))
                .onErrorResume(e -> Mono.just(false));
    }

    /**
     * Evict all accounts list from cache.
     */
    public Mono<Boolean> evictAll() {
        return redisTemplate.delete(ALL_ACCOUNTS_KEY)
                .map(deletedCount -> {
                    if (deletedCount > 0) {
                        log.debug("Cache evicted for all accounts");
                    }
                    return deletedCount > 0;
                })
                .doOnError(e -> log.error("Failed to evict all accounts cache", e))
                .onErrorResume(e -> Mono.just(false));
    }

    /**
     * Evict both single account and all accounts lists.
     */
    public Mono<Boolean> evictByIdAndAll(Long id) {
        return evictById(id)
                .zipWith(evictAll())
                .map(tuple -> tuple.getT1() || tuple.getT2())
                .onErrorResume(e -> {
                    log.error("Failed to evict combined caches for id: {}", id, e);
                    return Mono.just(false);
                });
    }
}
