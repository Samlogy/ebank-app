package com.ebank.auth.application.port.in;

import com.ebank.auth.application.dto.TokenClaimsDto;

public interface ValidateTokenUseCase {
    TokenClaimsDto validate(String token);
}
