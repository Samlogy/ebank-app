package com.ebank.gateway.config;

import com.ebank.gateway.filter.AuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines all gateway routes.
 *
 * <p>Public routes (auth, chatbot) have no authentication filter.
 * All other routes require a valid JWT via {@link AuthenticationFilter}.
 */
@Configuration
public class GatewayConfig {

    private final AuthenticationFilter authenticationFilter;

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${accounts.service.url:http://localhost:8082}")
    private String accountsServiceUrl;

    @Value("${transaction.service.url:http://localhost:8083}")
    private String transactionServiceUrl;

    @Value("${chatbot.service.url:http://localhost:3001}")
    private String chatbotServiceUrl;

    @Autowired
    public GatewayConfig(AuthenticationFilter authenticationFilter) {
        this.authenticationFilter = authenticationFilter;
    }

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {

        return builder.routes()

                // ----------------------------------------------------------------
                // Public: authentication service — no JWT filter
                // ----------------------------------------------------------------
                .route("auth-route", r -> r
                        .path("/api/auth/**")
                        .uri(authServiceUrl))

                // ----------------------------------------------------------------
                // Protected: accounts service
                // ----------------------------------------------------------------
                .route("accounts-route", r -> r
                        .path("/api/accounts/**")
                        .filters(f -> f.filter(authenticationFilter))
                        .uri(accountsServiceUrl))

                // ----------------------------------------------------------------
                // Protected: transactions service
                // ----------------------------------------------------------------
                .route("transactions-route", r -> r
                        .path("/api/transactions/**")
                        .filters(f -> f.filter(authenticationFilter))
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
