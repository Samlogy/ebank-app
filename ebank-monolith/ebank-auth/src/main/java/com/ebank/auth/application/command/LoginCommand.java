package com.ebank.auth.application.command;

public record LoginCommand(String email, String rawPassword) {}
