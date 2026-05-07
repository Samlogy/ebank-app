package com.ebank.auth.application.port.out;

import com.ebank.auth.domain.UserRole;

import java.util.Date;

public interface JwtPort {
    String generateAccessToken(String email, UserRole role);
    String generateRefreshToken(String email);
    boolean validateToken(String token);
    String getEmailFromToken(String token);
    Date getExpirationFromToken(String token);
    long getAccessTokenTtlMs();
    long getRefreshTokenTtlMs();
}
