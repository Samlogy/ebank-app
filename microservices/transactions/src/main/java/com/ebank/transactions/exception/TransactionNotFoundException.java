package com.ebank.transactions.exception;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String message) {
        super(message);
    }

    public TransactionNotFoundException(String id, boolean byId) {
        super("Transaction not found with id: " + id);
    }
}
