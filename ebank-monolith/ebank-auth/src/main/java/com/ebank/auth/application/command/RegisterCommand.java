package com.ebank.auth.application.command;

public record RegisterCommand(String username, String email, String rawPassword) {}
