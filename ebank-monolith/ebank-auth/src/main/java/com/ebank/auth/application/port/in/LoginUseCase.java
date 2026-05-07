package com.ebank.auth.application.port.in;

import com.ebank.auth.application.command.LoginCommand;
import com.ebank.auth.application.dto.AuthTokenDto;

public interface LoginUseCase {
    AuthTokenDto login(LoginCommand command);
}
