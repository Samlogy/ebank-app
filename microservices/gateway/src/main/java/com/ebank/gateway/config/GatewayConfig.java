package com.ebank.gateway.config;

import com.ebank.gateway.filter.AuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines all gateway routes.
 *
 * <p>Public routes (auth, chatbot) have no authentication filter, but the auth
 * route carries its own (tighter) Redis-backed rate limit since it is the one
 * route reachable without a token. Protected routes (accounts, transactions)
 * require a valid JWT via {@link AuthenticationFilter} and carry a more
 * generous rate limit as a defense-in-depth safety net.
 */
@Configuration
public class GatewayConfig {

    private final AuthenticationFilter authenticationFilter;
    private final RedisRateLimiter apiRateLimiter;
    private final RedisRateLimiter authRateLimiter;
    private final KeyResolver remoteAddressKeyResolver;

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${accounts.service.url:http://localhost:8082}")
    private String accountsServiceUrl;

    @Value("${transaction.service.url:http://localhost:8083}")
    private String transactionServiceUrl;

    @Value("${chatbot.service.url:http://localhost:3001}")
    private String chatbotServiceUrl;

    // Explicit @Qualifier is required here even though the parameter names match the
    // bean names: apiRateLimiter is @Primary (to satisfy spring-cloud-gateway's own
    // internal RequestRateLimiterGatewayFilterFactory bean, which needs exactly one
    // default RateLimiter), and @Primary wins over by-name matching during constructor
    // autowiring — without @Qualifier both parameters silently resolve to the same
    // primary bean.
    @Autowired
    public GatewayConfig(AuthenticationFilter authenticationFilter,
                          @Qualifier("apiRateLimiter") RedisRateLimiter apiRateLimiter,
                          @Qualifier("authRateLimiter") RedisRateLimiter authRateLimiter,
                          KeyResolver remoteAddressKeyResolver) {
        this.authenticationFilter = authenticationFilter;
        this.apiRateLimiter = apiRateLimiter;
        this.authRateLimiter = authRateLimiter;
        this.remoteAddressKeyResolver = remoteAddressKeyResolver;
    }

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {

        return builder.routes()

                // ----------------------------------------------------------------
                // Public: authentication service — no JWT filter, tight rate limit
                // ----------------------------------------------------------------
                .route("auth-route", r -> r
                        .path("/api/auth/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(authRateLimiter)
                                .setKeyResolver(remoteAddressKeyResolver)))
                        .uri(authServiceUrl))

                // ----------------------------------------------------------------
                // Protected: accounts service
                // ----------------------------------------------------------------
                .route("accounts-route", r -> r
                        .path("/api/accounts/**")
                        .filters(f -> f
                                .filter(authenticationFilter)
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(apiRateLimiter)
                                        .setKeyResolver(remoteAddressKeyResolver)))
                        .uri(accountsServiceUrl))

                // ----------------------------------------------------------------
                // Protected: transactions service
                // ----------------------------------------------------------------
                .route("transactions-route", r -> r
                        .path("/api/transactions/**")
                        .filters(f -> f
                                .filter(authenticationFilter)
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(apiRateLimiter)
                                        .setKeyResolver(remoteAddressKeyResolver)))
                        .uri(transactionServiceUrl))

                // ----------------------------------------------------------------
                // Public: chatbot WebSocket endpoint
                // ----------------------------------------------------------------
                .route("chatbot-ws-route", r -> r
                        .path("/ws/chat/**")
                        .uri(chatbotServiceUrl))

                // ----------------------------------------------------------------
                // Public: chatbot HTTP + SSE endpoints
                // ----------------------------------------------------------------
                .route("chatbot-http-route", r -> r
                        .path("/api/chat/**")
                        .uri(chatbotServiceUrl))

                .build();
    }
}
