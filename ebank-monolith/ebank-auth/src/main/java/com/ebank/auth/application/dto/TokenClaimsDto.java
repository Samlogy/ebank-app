package com.ebank.auth.application.dto;

public record TokenClaimsDto(String email, String role, Long userId) {}
