package com.ebank.auth.application.port.in;

import com.ebank.auth.application.command.RegisterCommand;
import com.ebank.auth.application.dto.AuthTokenDto;

public interface RegisterUseCase {
    AuthTokenDto register(RegisterCommand command);
}
