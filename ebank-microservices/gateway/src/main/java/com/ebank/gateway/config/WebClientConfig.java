package com.ebank.gateway.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Explicit WebClient.Builder bean.
 *
 * spring-cloud-starter-gateway-server-webflux does not trigger
 * WebClientAutoConfiguration, so WebClient.Builder must be registered manually.
 * ObservationRegistry is optional — if present it wires Micrometer tracing into
 * outgoing HTTP calls (traceparent propagation); if absent the builder still works.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder webClientBuilder(ObjectProvider<ObservationRegistry> registryProvider) {
        WebClient.Builder builder = WebClient.builder();
        ObservationRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            builder.observationRegistry(registry);
        }
        return builder;
    }
}
