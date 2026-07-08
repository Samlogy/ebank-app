package com.ebank.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Redis-backed request rate limiting at the edge.
 *
 * <p>WHY per-client-IP and not per-user: the rate limiter must also protect the
 * public, unauthenticated {@code /api/auth/**} route (login/register) from
 * credential-stuffing and brute-force traffic, where there is no authenticated
 * identity yet. Keying everything off the remote IP keeps one consistent
 * strategy across public and protected routes.
 *
 * <p>WHY the real socket address and not X-Forwarded-For: this gateway is the
 * single point of ingress (see helm gateway-ingress.yaml) and trusting a
 * client-supplied header here would let anyone bypass the limiter by rotating
 * the header value. If a trusted reverse proxy is later placed in front of the
 * gateway, switch the resolver to read the proxy's forwarded header instead.
 *
 * <p>Uses Spring Cloud Gateway's built-in {@link RedisRateLimiter} (token
 * bucket implemented as a Lua script against Redis — atomic, works correctly
 * across multiple gateway replicas since the bucket state lives in Redis, not
 * in-process).
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver remoteAddressKeyResolver() {
        return exchange -> {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String ip = (remoteAddress != null && remoteAddress.getAddress() != null)
                    ? remoteAddress.getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }

    /**
     * Applied to protected routes (accounts, transactions). Generous defaults —
     * this is a safety net against runaway/abusive clients, not the primary
     * throttling mechanism.
     */
    @Bean
    @Primary // both routes set their limiter explicitly; this just satisfies
             // spring-cloud-gateway's own auto-configured default RateLimiter bean lookup.
    public RedisRateLimiter apiRateLimiter(
            @Value("${gateway.rate-limit.api.replenish-rate:20}") int replenishRate,
            @Value("${gateway.rate-limit.api.burst-capacity:40}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity, 1);
    }

    /**
     * Applied to the public auth route. Deliberately tight — this is the one
     * route an attacker can hit without a valid token, so it is the one most
     * worth protecting against brute force.
     */
    @Bean
    public RedisRateLimiter authRateLimiter(
            @Value("${gateway.rate-limit.auth.replenish-rate:5}") int replenishRate,
            @Value("${gateway.rate-limit.auth.burst-capacity:10}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity, 1);
    }
}
