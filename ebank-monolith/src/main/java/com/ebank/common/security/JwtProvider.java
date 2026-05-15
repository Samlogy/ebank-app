package com.ebank.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
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
                .setHeaderParam("kid", keyStore.currentKid())
                .setSubject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(keyStore.currentPrivateKey(), SignatureAlgorithm.RS256)
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

    @SuppressWarnings("deprecation")
    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                    @Override
                    public Key resolveSigningKey(JwsHeader header, Claims claims) {
                        return keyStore.findPublicKey((String) header.get("kid"));
                    }
                })
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
