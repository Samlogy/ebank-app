package com.ebank.gateway.filter;

import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global logging filter.
 * Reads the traceId from Micrometer Tracing when available (OTel bridge),
 * ensuring log traceId matches the distributed trace in Tempo.
 * Uses ObjectProvider so the gateway starts even when the Tracer bean is
 * not auto-configured (Spring Cloud Gateway excludes some Micrometer auto-configs).
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final ObjectProvider<Tracer> tracerProvider;

    public LoggingFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        final long startTime = System.currentTimeMillis();
        String method = request.getMethod().name();
        String path   = request.getURI().getPath();

        Tracer tracer = tracerProvider.getIfAvailable();
        var   span    = (tracer != null) ? tracer.currentSpan() : null;
        String traceId = (span != null) ? span.context().traceId() : "no-trace";

        log.info("[GATEWAY] --> {} {} traceId={}", method, path, traceId);

        // Forward traceId to downstream services via X-Trace-Id header
        // (W3C traceparent is also propagated automatically by Micrometer Tracing)
        ServerHttpRequest mutated = request.mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build())
                .doOnSuccess(v -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    ServerHttpResponse response = exchange.getResponse();
                    int status = response.getStatusCode() != null
                            ? response.getStatusCode().value() : 0;
                    log.info("[GATEWAY] <-- {} {}ms traceId={}", status, elapsed, traceId);
                })
                .doOnError(err -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.error("[GATEWAY] <-- ERROR {}ms traceId={} msg={}",
                            elapsed, traceId, err.getMessage());
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
