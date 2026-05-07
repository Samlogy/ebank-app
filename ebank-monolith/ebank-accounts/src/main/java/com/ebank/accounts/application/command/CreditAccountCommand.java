package com.ebank.accounts.application.command;

import java.math.BigDecimal;

public record CreditAccountCommand(Long accountId, BigDecimal amount) {}
