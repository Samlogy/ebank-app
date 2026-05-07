package com.ebank.accounts.application.command;

import java.math.BigDecimal;

public record DebitAccountCommand(Long accountId, BigDecimal amount) {}
