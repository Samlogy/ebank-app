package com.ebank.accounts.application.port.in;

import com.ebank.accounts.application.command.CreditAccountCommand;

public interface CreditAccountUseCase {
    void credit(CreditAccountCommand command);
}
