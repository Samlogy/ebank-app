package com.ebank.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    /**
     * Shared registry — individual circuit breakers are fetched by name in each client.
     *
     * Tuning guide:
     *   slidingWindowSize          — number of calls used to compute the failure rate
     *   failureRateThreshold       — open the circuit when failure-% exceeds this value
     *   waitDurationInOpenState    — how long to stay open before trying a probe batch
     *   permittedCallsInHalfOpen   — probe batch size; success closes the circuit
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        return CircuitBreakerRegistry.of(config);
    }
}
