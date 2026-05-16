package com.ebank.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Standard OAuth2/OIDC JWKS endpoint.
 * Clients (or API gateways) call this to retrieve the current public keys
 * and verify JWT signatures without sharing a secret.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtKeyStore keyStore;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", keyStore.activeJwks());
    }
}
