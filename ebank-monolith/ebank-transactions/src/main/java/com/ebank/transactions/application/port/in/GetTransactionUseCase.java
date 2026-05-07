package com.ebank.transactions.application.port.in;

import com.ebank.transactions.application.dto.TransactionDto;

import java.util.List;

public interface GetTransactionUseCase {
    TransactionDto getById(Long id);
    List<TransactionDto> getAll();
    List<TransactionDto> getByAccountId(Long accountId);
}
