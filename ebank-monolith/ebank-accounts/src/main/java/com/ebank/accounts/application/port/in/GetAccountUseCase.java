package com.ebank.accounts.application.port.in;

import com.ebank.accounts.application.dto.AccountDto;

import java.util.List;

public interface GetAccountUseCase {
    AccountDto getById(Long id);
    List<AccountDto> getAll();
}
