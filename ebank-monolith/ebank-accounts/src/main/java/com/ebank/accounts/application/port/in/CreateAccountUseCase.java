package com.ebank.accounts.application.port.in;

import com.ebank.accounts.application.command.CreateAccountCommand;
import com.ebank.accounts.application.dto.AccountDto;

public interface CreateAccountUseCase {
    AccountDto create(CreateAccountCommand command);
}
