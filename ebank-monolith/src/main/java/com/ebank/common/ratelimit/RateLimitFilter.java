package com.ebank.common.ratelimit;

import com.ebank.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter scoped to POST /api/v1/auth/login.
 * One counter per source IP; window and capacity are configurable via
 * rate-limiting.login.* properties. No external library required.
 * For multi-node deployments replace with a distributed counter (Redis INCR).
 */
@Component
@Order(Integer.MIN_VALUE + 1)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Value("${rate-limiting.login.capacity:10}")
    private int capacity;

    @Value("${rate-limiting.login.refill-minutes:1}")
    private int refillMinutes;

    // long[0] = request count in current window, long[1] = window start epoch ms
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && "/api/v1/auth/login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String ip = resolveClientIp(request);

        if (tryConsume(ip)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded: ip={}", ip);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error("Too many login attempts. Please try again later."));
        }
    }

    private boolean tryConsume(String ip) {
        long now = System.currentTimeMillis();
        long windowMs = (long) refillMinutes * 60_000L;

        long[] window = windows.compute(ip, (k, v) -> {
            if (v == null || now - v[1] >= windowMs) {
                return new long[]{1L, now};
            }
            return new long[]{v[0] + 1L, v[1]};
        });

        return window[0] <= capacity;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
