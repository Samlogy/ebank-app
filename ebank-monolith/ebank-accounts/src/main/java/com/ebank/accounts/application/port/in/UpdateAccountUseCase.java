package com.ebank.accounts.application.port.in;

import com.ebank.accounts.application.command.UpdateAccountCommand;
import com.ebank.accounts.application.dto.AccountDto;

public interface UpdateAccountUseCase {
    AccountDto update(UpdateAccountCommand command);
}
