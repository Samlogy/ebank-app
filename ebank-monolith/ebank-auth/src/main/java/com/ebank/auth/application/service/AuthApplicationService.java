package com.ebank.auth.application.service;

import com.ebank.auth.application.command.LoginCommand;
import com.ebank.auth.application.command.RegisterCommand;
import com.ebank.auth.application.dto.AuthTokenDto;
import com.ebank.auth.application.dto.TokenClaimsDto;
import com.ebank.auth.application.port.in.*;
import com.ebank.auth.application.port.out.JwtPort;
import com.ebank.auth.application.port.out.RefreshTokenPort;
import com.ebank.auth.application.port.out.TokenBlacklistPort;
import com.ebank.auth.application.port.out.UserRepositoryPort;
import com.ebank.auth.domain.User;
import com.ebank.core.exception.BusinessRuleViolationException;
import com.ebank.core.exception.ResourceNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Date;

@Slf4j
@Service
class AuthApplicationService implements RegisterUseCase, LoginUseCase, LogoutUseCase,
        RefreshTokenUseCase, ValidateTokenUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtPort jwtPort;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistPort blacklistPort;
    private final RefreshTokenPort refreshTokenPort;

    private final Counter registerCounter;
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter logoutCounter;

    AuthApplicationService(UserRepositoryPort userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtPort jwtPort,
                           AuthenticationManager authenticationManager,
                           TokenBlacklistPort blacklistPort,
                           RefreshTokenPort refreshTokenPort,
                           MeterRegistry registry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtPort = jwtPort;
        this.authenticationManager = authenticationManager;
        this.blacklistPort = blacklistPort;
        this.refreshTokenPort = refreshTokenPort;
        this.registerCounter = counter(registry, "register", "success");
        this.loginSuccessCounter = counter(registry, "login", "success");
        this.loginFailureCounter = counter(registry, "login", "failure");
        this.logoutCounter = counter(registry, "logout", "success");
    }

    @Override
    @Transactional
    public AuthTokenDto register(RegisterCommand cmd) {
        if (userRepository.existsByEmail(cmd.email())) {
            throw new BusinessRuleViolationException("Email already in use: " + cmd.email());
        }
        if (userRepository.existsByUsername(cmd.username())) {
            throw new BusinessRuleViolationException("Username already taken: " + cmd.username());
        }

        User user = User.builder()
                .username(cmd.username())
                .email(cmd.email())
                .password(passwordEncoder.encode(cmd.rawPassword()))
                .build();
        user = userRepository.save(user);

        registerCounter.increment();
        log.info("Registered new user: {}", user.getEmail());
        return buildToken(user);
    }

    @Override
    public AuthTokenDto login(LoginCommand cmd) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(cmd.email(), cmd.rawPassword()));
        } catch (BadCredentialsException e) {
            loginFailureCounter.increment();
            throw e;
        }

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + auth.getName()));

        loginSuccessCounter.increment();
        log.info("User logged in: {}", user.getEmail());
        return buildToken(user);
    }

    @Override
    public AuthTokenDto refresh(String refreshToken) {
        if (!jwtPort.validateToken(refreshToken)) {
            throw new BusinessRuleViolationException("Invalid or expired refresh token");
        }
        String email = jwtPort.getEmailFromToken(refreshToken);
        String stored = refreshTokenPort.get(email)
                .orElseThrow(() -> new BusinessRuleViolationException("Refresh token not found or already revoked"));

        if (!stored.equals(refreshToken)) {
            throw new BusinessRuleViolationException("Refresh token mismatch");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        log.info("Token refreshed for user: {}", email);
        return buildToken(user);
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        if (!jwtPort.validateToken(accessToken)) {
            throw new BusinessRuleViolationException("Invalid token");
        }
        String email = jwtPort.getEmailFromToken(accessToken);
        Date expiration = jwtPort.getExpirationFromToken(accessToken);
        long remainingMs = expiration.getTime() - System.currentTimeMillis();
        if (remainingMs > 0) {
            blacklistPort.blacklist(accessToken, Duration.ofMillis(remainingMs));
        }
        refreshTokenPort.revoke(email);
        logoutCounter.increment();
        log.info("User logged out: {}", email);
    }

    @Override
    public TokenClaimsDto validate(String token) {
        if (!jwtPort.validateToken(token)) {
            throw new BusinessRuleViolationException("Invalid or expired token");
        }
        if (blacklistPort.isBlacklisted(token)) {
            throw new BusinessRuleViolationException("Token has been revoked");
        }
        String email = jwtPort.getEmailFromToken(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return new TokenClaimsDto(email, user.getRole().name(), user.getId());
    }

    private AuthTokenDto buildToken(User user) {
        String accessToken = jwtPort.generateAccessToken(user.getEmail(), user.getRole());
        String refreshToken = jwtPort.generateRefreshToken(user.getEmail());
        refreshTokenPort.store(user.getEmail(), refreshToken, Duration.ofMillis(jwtPort.getRefreshTokenTtlMs()));
        return new AuthTokenDto(accessToken, refreshToken, jwtPort.getAccessTokenTtlMs(),
                user.getUsername(), user.getEmail(), user.getRole().name());
    }

    private static Counter counter(MeterRegistry registry, String operation, String status) {
        return Counter.builder("auth.operations")
                .tag("operation", operation).tag("status", status)
                .register(registry);
    }
}
