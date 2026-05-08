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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
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
        
        String token = jwtProvider.generateToken(savedUser.getId(), savedUser.getEmail());
        
        return AuthResponse.builder()
            .token(token)
            .email(savedUser.getEmail())
            .fullName(savedUser.getFullName())
            .build();
    }
    
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        
        String token = jwtProvider.generateToken(user.getId(), user.getEmail());
        
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
