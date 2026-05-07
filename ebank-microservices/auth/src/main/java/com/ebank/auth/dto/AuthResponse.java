package com.ebank.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        String username,
        String email,
        String role
) {
    public AuthResponse(String accessToken, String refreshToken, long expiresIn,
                        String username, String email, String role) {
        this(accessToken, refreshToken, expiresIn, "Bearer", username, email, role);
    }
}
