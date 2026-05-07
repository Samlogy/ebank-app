package com.ebank.auth.application.port.out;

import java.time.Duration;

public interface TokenBlacklistPort {
    void blacklist(String token, Duration ttl);
    boolean isBlacklisted(String token);
}
