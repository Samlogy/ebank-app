package com.ebank.auth.api;

import com.ebank.auth.dto.AuthResponse;
import com.ebank.auth.dto.LoginRequest;
import com.ebank.auth.dto.RefreshTokenRequest;
import com.ebank.auth.dto.RegisterRequest;
import com.ebank.auth.security.JwtTokenProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ebank.auth.exception.BusinessException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final StringRedisTemplate redisTemplate;

    private final Counter registerCounter;
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter logoutCounter;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AuthenticationManager authenticationManager,
                       StringRedisTemplate redisTemplate,
                       MeterRegistry registry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.redisTemplate = redisTemplate;
        this.registerCounter = Counter.builder("auth.operations")
                .tag("operation", "register").tag("status", "success")
                .description("Number of successful user registrations")
                .register(registry);
        this.loginSuccessCounter = Counter.builder("auth.operations")
                .tag("operation", "login").tag("status", "success")
                .description("Number of successful logins")
                .register(registry);
        this.loginFailureCounter = Counter.builder("auth.operations")
                .tag("operation", "login").tag("status", "failure")
                .description("Number of failed login attempts (bad credentials)")
                .register(registry);
        this.logoutCounter = Counter.builder("auth.operations")
                .tag("operation", "logout").tag("status", "success")
                .description("Number of logouts")
                .register(registry);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email already in use: " + request.email());
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("Username already taken: " + request.username());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role("ROLE_USER")
                .active(true)
                .build();

        userRepository.save(user);
        registerCounter.increment();
        log.info("Registered new user: {}", user.getEmail());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        storeRefreshToken(user.getEmail(), refreshToken);

        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.getExpiration(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException e) {
            loginFailureCounter.increment();
            throw e;
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        String accessToken = jwtTokenProvider.generateAccessToken(email, user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(email);

        storeRefreshToken(email, refreshToken);
        loginSuccessCounter.increment();
        log.info("User logged in: {}", email);

        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.getExpiration(),
                user.getUsername(),
                email,
                user.getRole()
        );
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String token = request.refreshToken();

        if (!jwtTokenProvider.validateToken(token)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        String email = jwtTokenProvider.getUsernameFromToken(token);
        String storedToken = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + email);

        if (storedToken == null || !storedToken.equals(token)) {
            throw new RuntimeException("Refresh token not found or already revoked");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        String newAccessToken = jwtTokenProvider.generateAccessToken(email, user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email);

        storeRefreshToken(email, newRefreshToken);
        log.info("Token refreshed for user: {}", email);

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                jwtTokenProvider.getExpiration(),
                user.getUsername(),
                email,
                user.getRole()
        );
    }

    public void logout(String accessToken) {
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new RuntimeException("Invalid token");
        }

        String email = jwtTokenProvider.getUsernameFromToken(accessToken);
        Date expiration = jwtTokenProvider.getExpirationFromToken(accessToken);
        long remainingTtl = expiration.getTime() - System.currentTimeMillis();

        if (remainingTtl > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_KEY_PREFIX + accessToken,
                    "1",
                    remainingTtl,
                    TimeUnit.MILLISECONDS
            );
        }

        redisTemplate.delete(REFRESH_KEY_PREFIX + email);
        logoutCounter.increment();
        log.info("User logged out: {}", email);
    }

    public Map<String, String> validateToken(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw new RuntimeException("Invalid or expired token");
        }
        Boolean isBlacklisted = redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token);
        if (Boolean.TRUE.equals(isBlacklisted)) {
            throw new RuntimeException("Token has been revoked");
        }
        String email = jwtTokenProvider.getUsernameFromToken(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
        return Map.of("username", email, "role", user.getRole(), "userId", user.getId().toString());
    }

    private void storeRefreshToken(String email, String refreshToken) {
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + email,
                refreshToken,
                jwtTokenProvider.getRefreshExpiration(),
                TimeUnit.MILLISECONDS
        );
    }
}
