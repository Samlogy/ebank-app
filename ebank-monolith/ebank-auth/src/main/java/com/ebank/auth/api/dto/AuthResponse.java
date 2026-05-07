package com.ebank.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication token response")
public record AuthResponse(
        @Schema(description = "JWT access token") String accessToken,
        @Schema(description = "JWT refresh token") String refreshToken,
        @Schema(description = "Access token TTL in milliseconds") long expiresIn,
        String username,
        String email,
        String role
) {}
