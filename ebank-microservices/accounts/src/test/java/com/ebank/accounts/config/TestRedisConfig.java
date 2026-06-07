package com.ebank.accounts.config;

import com.ebank.accounts.api.Account;
import com.ebank.accounts.cache.AccountCacheService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Test configuration that provides a no-op mock of AccountCacheService.
 * This prevents integration tests from requiring a real Redis instance.
 */
@TestConfiguration
public class TestRedisConfig {

    /**
     * Provide a pass-through cache service that does nothing.
     * All cache operations return empty/false, so the query handler
     * always hits the database in tests.
     */
    @Bean
    @Primary
    public AccountCacheService accountCacheService() {
        return new AccountCacheService(null, null, new SimpleMeterRegistry()) {
            @Override
            public Mono<Account> getCachedById(Long id) {
                return Mono.empty();
            }

            @Override
            public Mono<List<Account>> getCachedAll() {
                return Mono.empty();
            }

            @Override
            public Mono<Boolean> putById(Account account) {
                return Mono.just(false);
            }

            @Override
            public Mono<Boolean> putAll(List<Account> accounts) {
                return Mono.just(false);
            }

            @Override
            public Mono<Boolean> evictById(Long id) {
                return Mono.just(false);
            }

            @Override
            public Mono<Boolean> evictAll() {
                return Mono.just(false);
            }

            @Override
            public Mono<Boolean> evictByIdAndAll(Long id) {
                return Mono.just(false);
            }
        };
    }
}
