package com.ebank.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

public class JwtUtil {

    private final SecretKey secretKey;

    public JwtUtil(String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates the JWT token's signature and expiration.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return true if the token is valid and not expired, false otherwise
     */
    public boolean isValid(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return claims.getExpiration() != null
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extracts the subject (username) from a valid JWT token.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return the subject claim value
     */
    public String getUsername(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Time remaining before the token expires — used to cap how long the gateway
     * is allowed to cache a validation result for it. Returns {@link Duration#ZERO}
     * if the token is invalid, unparsable, or already expired.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     */
    public Duration getRemainingValidity(String token) {
        try {
            Date expiration = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration();

            if (expiration == null) {
                return Duration.ZERO;
            }
            Duration remaining = Duration.between(new Date().toInstant(), expiration.toInstant());
            return remaining.isNegative() ? Duration.ZERO : remaining;
        } catch (JwtException | IllegalArgumentException e) {
            return Duration.ZERO;
        }
    }
}
