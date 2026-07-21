package com.ebank.chatbot.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * {@link RestClient} beans for the downstream banking microservices.
 *
 * <p>The chatbot never touches those databases directly — it calls the same public
 * REST APIs the frontend uses, so it always sees authoritative, service-owned data
 * (Database-per-Service is preserved). Base URLs come from Vault / env, defaulting to
 * localhost for local dev.
 */
@Configuration
public class DownstreamClientsConfig {

    @Value("${accounts.service.url:http://localhost:8082}")
    private String accountsServiceUrl;

    @Value("${transaction.service.url:http://localhost:8083}")
    private String transactionServiceUrl;

    private RestClient build(String baseUrl) {
        // Short timeouts: a chatbot tool call must fail fast and degrade gracefully
        // rather than block the LLM turn waiting on a slow/unreachable service.
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Bean
    public RestClient accountsRestClient() {
        return build(accountsServiceUrl);
    }

    @Bean
    public RestClient transactionsRestClient() {
        return build(transactionServiceUrl);
    }
}
