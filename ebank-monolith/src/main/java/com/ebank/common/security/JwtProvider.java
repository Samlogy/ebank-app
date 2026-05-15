package com.ebank.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtKeyStore keyStore;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public String generateToken(Long userId, String email, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtExpiration);
        return Jwts.builder()
                .header().add("kid", keyStore.currentKid()).and()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(keyStore.currentPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    public String getRoleFromToken(String token) {
        return parse(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .keyLocator(header -> keyStore.findPublicKey((String) header.get("kid")))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
