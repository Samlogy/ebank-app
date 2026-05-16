package com.ebank.common.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the current and previous RSA-2048 key pairs used to sign JWTs.
 *
 * Zero-downtime rotation: calling rotate() shifts current → previous and
 * generates a new current. Tokens signed with the old key remain verifiable
 * until they expire because the previous public key is still in the JWKS.
 */
@Component
@Slf4j
public class JwtKeyStore {

    public record KeyEntry(String kid, RSAPublicKey publicKey, RSAPrivateKey privateKey) {}

    private volatile KeyEntry current;
    private volatile KeyEntry previous;

    @PostConstruct
    public void init() {
        current = generate();
        log.info("JWT RSA-2048 key pair initialised: kid={}", current.kid());
    }

    public synchronized void rotate() {
        previous = current;
        current = generate();
        log.info("JWT keys rotated: current.kid={} previous.kid={}",
                current.kid(), previous != null ? previous.kid() : "none");
    }

    public RSAPrivateKey currentPrivateKey() {
        return current.privateKey();
    }

    public String currentKid() {
        return current.kid();
    }

    /** Returns the public key for the given kid, or null if unknown (rejects the token). */
    public RSAPublicKey findPublicKey(String kid) {
        if (kid == null) return null;
        if (current != null && current.kid().equals(kid)) return current.publicKey();
        if (previous != null && previous.kid().equals(kid)) return previous.publicKey();
        return null;
    }

    /**
     * Returns JWKS-format maps for every active public key.
     * Built manually from the RSA modulus/exponent to avoid JJWT Jwks API volatility.
     */
    public List<Map<String, Object>> activeJwks() {
        List<Map<String, Object>> keys = new ArrayList<>();
        if (current != null)  keys.add(toJwk(current));
        if (previous != null) keys.add(toJwk(previous));
        return keys;
    }

    // ── internals ──────────────────────────────────────────────────────────────

    private Map<String, Object> toJwk(KeyEntry entry) {
        RSAPublicKey pub = entry.publicKey();
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", entry.kid());
        jwk.put("n",   enc.encodeToString(unsignedBytes(pub.getModulus())));
        jwk.put("e",   enc.encodeToString(unsignedBytes(pub.getPublicExponent())));
        return jwk;
    }

    /** BigInteger.toByteArray() prepends a 0x00 sign byte for positive values whose
     *  high bit is 1 — strip it so the JWK integers are unsigned, as RFC 7518 requires. */
    private byte[] unsignedBytes(BigInteger n) {
        byte[] bytes = n.toByteArray();
        if (bytes[0] == 0x00) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }

    private KeyEntry generate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            var kp = gen.generateKeyPair();
            return new KeyEntry(
                    UUID.randomUUID().toString(),
                    (RSAPublicKey) kp.getPublic(),
                    (RSAPrivateKey) kp.getPrivate());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }
}
