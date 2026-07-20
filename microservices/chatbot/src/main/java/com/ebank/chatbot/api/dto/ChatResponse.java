package com.ebank.chatbot.api.dto;

import java.time.Instant;

/** Outbound chat answer. */
public record ChatResponse(String sessionId, String response, Instant timestamp) {

    public static ChatResponse of(String sessionId, String response) {
        return new ChatResponse(sessionId, response, Instant.now());
    }
}
