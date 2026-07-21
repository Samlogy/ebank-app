package com.ebank.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound chat message. {@code sessionId} is optional — when omitted the server
 * generates one and returns it, so the client can continue the conversation.
 */
public record ChatRequest(
        @NotBlank(message = "message must not be blank") String message,
        String sessionId) {
}
