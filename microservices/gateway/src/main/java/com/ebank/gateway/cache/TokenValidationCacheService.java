package com.ebank.gateway.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Cache-aside layer in front of the auth-service's {@code POST /api/auth/validate}
 * call.
 *
 * <p>WHY: without this cache, every single request to a protected route (accounts,
 * transactions, ...) triggers a synchronous HTTP round-trip from the gateway to the
 * auth-service just to re-verify a token that was already validated moments ago.
 * That turns auth-service into a hard dependency for the entire API surface and
 * multiplies its load by the request volume of every other service combined.
 *
 * <p>Cache key is a SHA-256 hash of the raw JWT (not the token itself — tokens are
 * bearer credentials and Redis keys can show up in logs/monitoring, so we avoid
 * persisting them verbatim). TTL is capped to the token's remaining lifetime so a
 * cached entry can never outlive the JWT it represents.
 */
@Slf4j
@Service
public class TokenValidationCacheService {

    private static final String KEY_PREFIX = "gateway:token-validation:";

    /** Same key format auth-service writes to on logout — see AuthService#logout. */
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public TokenValidationCacheService(ReactiveStringRedisTemplate redisTemplate,
                                        ObjectMapper objectMapper,
                                        MeterRegistry registry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheHitCounter = Counter.builder("gateway.token_validation.cache")
                .tag("result", "hit")
                .description("Number of requests served from the gateway JWT validation cache")
                .register(registry);
        this.cacheMissCounter = Counter.builder("gateway.token_validation.cache")
                .tag("result", "miss")
                .description("Number of requests that required calling auth-service to validate the token")
                .register(registry);
    }

    public record ValidatedIdentity(String username, String role) {
    }

    public Mono<ValidatedIdentity> getCached(String token) {
        String key = KEY_PREFIX + hash(token);
        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(value -> {
                    try {
                        ValidatedIdentity identity = objectMapper.readValue(value, ValidatedIdentity.class);
                        cacheHitCounter.increment();
                        return Mono.just(identity);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize cached token validation result", e);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.fromRunnable(cacheMissCounter::increment))
                .onErrorResume(e -> {
                    log.warn("Token validation cache read failed, falling back to auth-service: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Caches a validation result. {@code ttl} should already be capped by the
     * caller to the token's remaining lifetime (see {@code JwtUtil}).
     */
    public Mono<Boolean> put(String token, String username, String role, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return Mono.just(false);
        }
        String key = KEY_PREFIX + hash(token);
        try {
            String json = objectMapper.writeValueAsString(new ValidatedIdentity(username, role));
            return redisTemplate.opsForValue()
                    .set(key, json, ttl)
                    .onErrorResume(e -> {
                        log.warn("Failed to write token validation cache entry: {}", e.getMessage());
                        return Mono.just(false);
                    });
        } catch (Exception e) {
            log.warn("Failed to serialize token validation result for caching", e);
            return Mono.just(false);
        }
    }

    /** Evicts a cache entry — used when the blacklist check flags a cached token as revoked. */
    public Mono<Boolean> evict(String token) {
        return redisTemplate.opsForValue().delete(KEY_PREFIX + hash(token));
    }

    /**
     * Checks the shared blacklist Redis keyspace that auth-service writes to on logout.
     * Done on every request — cache hit or miss — so a revoked token stops working
     * immediately instead of waiting out the validation cache TTL. This single
     * {@code EXISTS} call is far cheaper than the full HTTP validate round-trip it
     * replaces on the cache-hit path.
     */
    public Mono<Boolean> isBlacklisted(String token) {
        return redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token)
                .onErrorResume(e -> {
                    log.warn("Blacklist check failed, treating token as not blacklisted: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM
            throw new IllegalStateException(e);
        }
    }
}
