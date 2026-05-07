package com.ebank.transactions.exception;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

import java.util.UUID;

@Configuration
public class TraceIdConfig {

    @Bean
    public WebFilter traceIdFilter() {
        return (exchange, chain) -> {
            String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString();
            }

            final String finalTraceId = traceId;
            MDC.put("traceId", finalTraceId);
            MDC.put("requestUri", exchange.getRequest().getURI().getPath());
            MDC.put("method", exchange.getRequest().getMethod().name());

            return chain.filter(
                    exchange.mutate()
                            .response(exchange.getResponse())
                            .build()
            ).doOnTerminate(MDC::clear)
             .contextWrite(ctx -> ctx.put("traceId", finalTraceId));
        };
    }
}
