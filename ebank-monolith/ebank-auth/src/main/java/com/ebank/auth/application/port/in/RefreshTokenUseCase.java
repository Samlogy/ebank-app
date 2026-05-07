package com.ebank.auth.application.port.in;

import com.ebank.auth.application.dto.AuthTokenDto;

public interface RefreshTokenUseCase {
    AuthTokenDto refresh(String refreshToken);
}
