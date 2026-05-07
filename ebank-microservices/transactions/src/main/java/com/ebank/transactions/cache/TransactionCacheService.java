package com.ebank.transactions.cache;

import com.ebank.transactions.api.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class TransactionCacheService {

    private static final String TX_BY_ID_KEY_PREFIX = "transaction:by-id:";
    private static final String TX_BY_ACCOUNT_KEY_PREFIX = "transaction:by-account:";
    private static final Duration TX_TTL = Duration.ofMinutes(10);
    private static final Duration TX_LIST_TTL = Duration.ofMinutes(5);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Mono<Transaction> getCachedById(String id) {
        String key = TX_BY_ID_KEY_PREFIX + id;
        return redisTemplate.opsForValue()
                .get(key)
                .doOnNext(v -> log.debug("Cache hit for transaction id: {}", id))
                .flatMap(value -> {
                    try {
                        return Mono.just(objectMapper.readValue(value, Transaction.class));
                    } catch (Exception e) {
                        log.error("Failed to deserialize transaction from cache for id: {}", id, e);
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Cache retrieval error for transaction id: {}", id, e);
                    return Mono.empty();
                });
    }

    public Mono<List<Transaction>> getCachedByAccount(String accountId) {
        String key = TX_BY_ACCOUNT_KEY_PREFIX + accountId;
        return redisTemplate.opsForValue()
                .get(key)
                .doOnNext(v -> log.debug("Cache hit for account transactions: {}", accountId))
                .flatMap(value -> {
                    try {
                        Transaction[] txs = objectMapper.readValue(value, Transaction[].class);
                        return Mono.just(Arrays.asList(txs));
                    } catch (Exception e) {
                        log.error("Failed to deserialize transactions from cache for accountId: {}", accountId, e);
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Cache retrieval error for accountId: {}", accountId, e);
                    return Mono.empty();
                });
    }

    public Mono<Boolean> putById(Transaction transaction) {
        if (transaction == null || transaction.getId() == null) {
            return Mono.just(false);
        }
        String key = TX_BY_ID_KEY_PREFIX + transaction.getId();
        try {
            String json = objectMapper.writeValueAsString(transaction);
            return redisTemplate.opsForValue()
                    .set(key, json, TX_TTL)
                    .doOnNext(ok -> log.debug("Cache set for transaction id: {}", transaction.getId()))
                    .onErrorResume(e -> {
                        log.error("Failed to cache transaction id: {}", transaction.getId(), e);
                        return Mono.just(false);
                    });
        } catch (Exception e) {
            log.error("Failed to serialize transaction for caching id: {}", transaction.getId(), e);
            return Mono.just(false);
        }
    }

    public Mono<Boolean> putByAccount(String accountId, List<Transaction> transactions) {
        if (accountId == null || transactions == null) {
            return Mono.just(false);
        }
        String key = TX_BY_ACCOUNT_KEY_PREFIX + accountId;
        try {
            String json = objectMapper.writeValueAsString(transactions);
            return redisTemplate.opsForValue()
                    .set(key, json, TX_LIST_TTL)
                    .doOnNext(ok -> log.debug("Cache set for account transactions: {}", accountId))
                    .onErrorResume(e -> {
                        log.error("Failed to cache transactions for accountId: {}", accountId, e);
                        return Mono.just(false);
                    });
        } catch (Exception e) {
            log.error("Failed to serialize transactions for caching accountId: {}", accountId, e);
            return Mono.just(false);
        }
    }

    public Mono<Boolean> evictById(String id) {
        String key = TX_BY_ID_KEY_PREFIX + id;
        return redisTemplate.delete(key)
                .map(count -> {
                    if (count > 0) log.debug("Cache evicted for transaction id: {}", id);
                    return count > 0;
                })
                .onErrorResume(e -> {
                    log.error("Failed to evict transaction cache id: {}", id, e);
                    return Mono.just(false);
                });
    }

    public Mono<Boolean> evictByAccount(String accountId) {
        String key = TX_BY_ACCOUNT_KEY_PREFIX + accountId;
        return redisTemplate.delete(key)
                .map(count -> {
                    if (count > 0) log.debug("Cache evicted for account transactions: {}", accountId);
                    return count > 0;
                })
                .onErrorResume(e -> {
                    log.error("Failed to evict account transactions cache: {}", accountId, e);
                    return Mono.just(false);
                });
    }
}
