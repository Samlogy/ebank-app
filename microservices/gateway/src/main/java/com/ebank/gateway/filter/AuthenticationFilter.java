package com.ebank.gateway.filter;

import com.ebank.gateway.cache.TokenValidationCacheService;
import com.ebank.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Authentication filter that validates JWTs without hitting auth-service on
 * every request.
 *
 * <p>Request flow:
 * <ol>
 *   <li>Cheap local checks first — header present, signature/expiry valid via
 *       {@link JwtUtil} — reject garbage tokens with zero I/O.</li>
 *   <li>Blacklist check against the Redis keyspace auth-service writes to on
 *       logout — always performed, cache hit or miss, so revocation is immediate.</li>
 *   <li>Validation-result cache lookup ({@link TokenValidationCacheService}) — on
 *       hit, the request proceeds using the cached username/role, no network call.</li>
 *   <li>On cache miss only, fall back to the original behaviour: call
 *       {@code POST /api/auth/validate} on auth-service, then populate the cache
 *       for subsequent requests bearing the same token.</li>
 * </ol>
 * Uses the auto-configured WebClient.Builder so Micrometer Tracing automatically
 * injects the W3C traceparent header into outbound calls — the auth-service span
 * becomes a child of the current gateway span in Tempo.
 */
@Slf4j
@Component
public class AuthenticationFilter implements GatewayFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final WebClient webClient;
    private final TokenValidationCacheService tokenValidationCacheService;
    private final JwtUtil jwtUtil;
    private final Duration maxCacheTtl;

    // WebClient.Builder is auto-configured by Spring Boot with Micrometer tracing
    // instrumentation — it propagates traceparent automatically to downstream services.
    public AuthenticationFilter(
            WebClient.Builder webClientBuilder,
            TokenValidationCacheService tokenValidationCacheService,
            JwtUtil jwtUtil,
            @Value("${auth.service.url:http://localhost:8081}") String authServiceUrl,
            @Value("${gateway.token-cache.max-ttl-seconds:60}") long maxCacheTtlSeconds) {
        this.webClient = webClientBuilder
                .baseUrl(authServiceUrl)
                .build();
        this.tokenValidationCacheService = tokenValidationCacheService;
        this.jwtUtil = jwtUtil;
        this.maxCacheTtl = Duration.ofSeconds(maxCacheTtlSeconds);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[AUTH] Missing or malformed Authorization header for path: {}", request.getURI().getPath());
            return unauthorized(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtUtil.isValid(token)) {
            log.warn("[AUTH] Locally-rejected (malformed/expired) token for path: {}", request.getURI().getPath());
            return unauthorized(exchange);
        }

        return tokenValidationCacheService.isBlacklisted(token)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        log.warn("[AUTH] Rejected blacklisted token for path: {}", request.getURI().getPath());
                        return tokenValidationCacheService.evict(token).then(unauthorized(exchange));
                    }
                    return tokenValidationCacheService.getCached(token)
                            .flatMap(identity -> proceed(exchange, chain, request, identity.username(), identity.role()))
                            .switchIfEmpty(Mono.defer(() -> validateViaAuthService(token, authHeader, exchange, chain, request)));
                });
    }

    private Mono<Void> validateViaAuthService(String token, String authHeader, ServerWebExchange exchange,
                                               GatewayFilterChain chain, ServerHttpRequest request) {
        return webClient.post()
                .uri("/api/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(body -> {
                    String username = (String) body.get("username");
                    String role = (String) body.get("role");
                    log.debug("[AUTH] Validated user '{}' (role={}) for path: {}", username, role, request.getURI().getPath());

                    Duration ttl = minDuration(maxCacheTtl, jwtUtil.getRemainingValidity(token));
                    return tokenValidationCacheService.put(token, username, role, ttl)
                            .then(proceed(exchange, chain, request, username, role));
                })
                .onErrorResume(ex -> {
                    log.warn("[AUTH] Token validation failed for path {}: {}", request.getURI().getPath(), ex.getMessage());
                    return unauthorized(exchange);
                });
    }

    private Mono<Void> proceed(ServerWebExchange exchange, GatewayFilterChain chain,
                                ServerHttpRequest request, String username, String role) {
        ServerHttpRequest mutated = request.mutate()
                .header("X-Authenticated-User", username)
                .header("X-Authenticated-Role", role)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private static Duration minDuration(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return errorResponse(exchange, HttpStatus.UNAUTHORIZED,
                "Missing, invalid, or expired Authorization token", "UNAUTHORIZED");
    }

    private Mono<Void> errorResponse(ServerWebExchange exchange, HttpStatus status,
                                     String message, String errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        String body = String.format(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"," +
                "\"path\":\"%s\",\"method\":\"%s\",\"timestamp\":\"%s\",\"errorCode\":\"%s\"}",
                status.value(), status.getReasonPhrase(), message,
                path, method, LocalDateTime.now(), errorCode);

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
