package com.ebank.common.ratelimit;

import com.ebank.common.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter for POST /api/v1/auth/login.
 * Uses Redis INCR+EXPIRE when Redis is available (multi-node safe),
 * falls back to in-process ConcurrentHashMap for local/test environments.
 */
@Component
@Order(Integer.MIN_VALUE + 1)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Nullable
    private final StringRedisTemplate redisTemplate;

    @Value("${rate-limiting.login.capacity:10}")
    private int capacity;

    @Value("${rate-limiting.login.refill-minutes:1}")
    private int refillMinutes;

    // Fallback: local window per source IP — not shared across nodes
    private final ConcurrentHashMap<String, long[]> localWindows = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
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
        if (redisTemplate != null) {
            return tryConsumeRedis(ip);
        }
        return tryConsumeLocal(ip);
    }

    private boolean tryConsumeRedis(String ip) {
        String key = "ebank:monolith:rate-limit:login:" + ip;
        Duration window = Duration.ofMinutes(refillMinutes);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // Set expiry only on first increment to avoid resetting the window on each request
                redisTemplate.expire(key, window);
            }
            return count != null && count <= capacity;
        } catch (Exception e) {
            log.warn("Redis rate-limit error, falling back to local counter: {}", e.getMessage());
            return tryConsumeLocal(ip);
        }
    }

    private boolean tryConsumeLocal(String ip) {
        long now = System.currentTimeMillis();
        long windowMs = (long) refillMinutes * 60_000L;

        long[] window = localWindows.compute(ip, (k, v) -> {
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
