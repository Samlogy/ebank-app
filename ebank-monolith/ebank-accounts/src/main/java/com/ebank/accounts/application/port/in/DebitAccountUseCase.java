package com.ebank.accounts.application.port.in;

import com.ebank.accounts.application.command.DebitAccountCommand;

public interface DebitAccountUseCase {
    void debit(DebitAccountCommand command);
}
