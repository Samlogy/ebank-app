package com.ebank.gateway.filter;

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
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Authentication filter that delegates JWT validation to the auth service.
 * Uses the auto-configured WebClient.Builder so Micrometer Tracing automatically
 * injects the W3C traceparent header into outbound calls — the auth-service span
 * becomes a child of the current gateway span in Tempo.
 */
@Slf4j
@Component
public class AuthenticationFilter implements GatewayFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final WebClient webClient;

    // WebClient.Builder is auto-configured by Spring Boot with Micrometer tracing
    // instrumentation — it propagates traceparent automatically to downstream services.
    public AuthenticationFilter(
            WebClient.Builder webClientBuilder,
            @Value("${auth.service.url:http://localhost:8081}") String authServiceUrl) {
        this.webClient = webClientBuilder
                .baseUrl(authServiceUrl)
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[AUTH] Missing or malformed Authorization header for path: {}", request.getURI().getPath());
            return unauthorized(exchange);
        }

        return webClient.post()
                .uri("/api/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(body -> {
                    String username = (String) body.get("username");
                    String role = (String) body.get("role");
                    log.debug("[AUTH] Validated user '{}' (role={}) for path: {}", username, role, request.getURI().getPath());

                    ServerHttpRequest mutated = request.mutate()
                            .header("X-Authenticated-User", username)
                            .header("X-Authenticated-Role", role)
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .onErrorResume(ex -> {
                    log.warn("[AUTH] Token validation failed for path {}: {}", request.getURI().getPath(), ex.getMessage());
                    return unauthorized(exchange);
                });
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
