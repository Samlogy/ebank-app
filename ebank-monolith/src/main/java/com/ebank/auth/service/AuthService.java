package com.ebank.auth.service;

import com.ebank.auth.dto.AuthResponse;
import com.ebank.auth.dto.LoginRequest;
import com.ebank.auth.dto.RegisterRequest;
import com.ebank.auth.dto.UserResponse;
import com.ebank.auth.entity.User;
import com.ebank.auth.repository.UserRepository;
import com.ebank.common.exception.InvalidCredentialsException;
import com.ebank.common.exception.ResourceNotFoundException;
import com.ebank.common.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        
        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName())
            .role("USER")
            .build();
        
        User savedUser = userRepository.save(user);
        log.info("User registered: userId={} email={}", savedUser.getId(), savedUser.getEmail());

        String token = jwtProvider.generateToken(savedUser.getId(), savedUser.getEmail(), savedUser.getRole());

        return AuthResponse.builder()
            .token(token)
            .email(savedUser.getEmail())
            .fullName(savedUser.getFullName())
            .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> {
                log.warn("Login failed: email={} reason=user_not_found", request.getEmail());
                return new InvalidCredentialsException("Invalid email or password");
            });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: userId={} email={} reason=wrong_password", user.getId(), user.getEmail());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        log.info("User logged in: userId={} email={}", user.getId(), user.getEmail());
        String token = jwtProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
        
        return AuthResponse.builder()
            .token(token)
            .email(user.getEmail())
            .fullName(user.getFullName())
            .build();
    }
    
    public UserResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        return UserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .build();
    }
}
