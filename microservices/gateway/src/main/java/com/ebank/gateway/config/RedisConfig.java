package com.ebank.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Reactive Redis access for the gateway — backs both the JWT validation
 * cache ({@link com.ebank.gateway.cache.TokenValidationCacheService}) and
 * the Redis-backed request rate limiter (RedisRateLimiter, auto-configured
 * by spring-cloud-gateway from the same connection factory).
 */
@Configuration
public class RedisConfig {

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    /**
     * Explicit classic Jackson (com.fasterxml) ObjectMapper — Spring Boot 4's
     * default auto-configured mapper is Jackson 3 (tools.jackson), which the
     * jjwt-jackson module and this cache's (de)serialization don't use. Same
     * pattern as accounts/config/AppConfig#objectMapper.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
