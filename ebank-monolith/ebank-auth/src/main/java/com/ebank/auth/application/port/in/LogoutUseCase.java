package com.ebank.auth.application.port.in;

public interface LogoutUseCase {
    void logout(String accessToken);
}
