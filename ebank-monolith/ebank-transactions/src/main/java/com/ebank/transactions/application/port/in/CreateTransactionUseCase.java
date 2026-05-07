package com.ebank.transactions.application.port.in;

import com.ebank.transactions.application.command.CreateTransactionCommand;
import com.ebank.transactions.application.dto.TransactionDto;

public interface CreateTransactionUseCase {
    TransactionDto create(CreateTransactionCommand command);
}
