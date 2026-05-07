package com.ebank.auth.application.dto;

public record AuthTokenDto(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String username,
        String email,
        String role
) {}
