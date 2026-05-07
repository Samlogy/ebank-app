package com.ebank.auth.application.port.out;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenPort {
    void store(String email, String token, Duration ttl);
    Optional<String> get(String email);
    void revoke(String email);
}
