package com.ebank.auth.controller;

import com.ebank.auth.dto.AuthResponse;
import com.ebank.auth.dto.LoginRequest;
import com.ebank.auth.dto.RegisterRequest;
import com.ebank.auth.dto.UserResponse;
import com.ebank.auth.service.AuthService;
import com.ebank.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "User registered successfully"));
    }
    
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }
    
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserResponse response = authService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "User profile retrieved"));
    }
}
