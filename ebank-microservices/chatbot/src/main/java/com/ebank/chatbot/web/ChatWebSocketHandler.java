package com.ebank.chatbot.web;

import java.util.Map;
import java.util.UUID;

import com.ebank.chatbot.service.ChatbotService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Streams assistant answers over a WebSocket. Each connection is greeted with a
 * generated {@code sessionId}; inbound frames are JSON {@code {"message": "..."}} and
 * the answer is streamed back as {@code {"type":"token","content":"..."}} frames,
 * terminated by {@code {"type":"done"}}.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final String SESSION_ID = "chatSessionId";

    private final ChatbotService chatbotService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatWebSocketHandler(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        session.getAttributes().put(SESSION_ID, sessionId);
        send(session, Map.of("type", "connected", "sessionId", sessionId,
                "message", "Connected to eBank Assistant. How can I help you?"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = (String) session.getAttributes().get(SESSION_ID);
        try {
            Map<?, ?> payload = objectMapper.readValue(message.getPayload(), Map.class);
            Object text = payload.get("message");
            if (text == null || text.toString().isBlank()) {
                send(session, Map.of("type", "error", "message", "Empty message."));
                return;
            }
            // Stream tokens back as they arrive; block the WS worker thread until complete.
            chatbotService.stream(sessionId, text.toString())
                    .doOnNext(token -> send(session, Map.of("type", "token", "content", token)))
                    .doOnError(err -> send(session, Map.of("type", "error",
                            "message", "An error occurred: " + err.getMessage())))
                    .doOnComplete(() -> send(session, Map.of("type", "done", "sessionId", sessionId)))
                    .blockLast();
        } catch (Exception e) {
            log.error("[ws] error handling message for session {}", sessionId, e);
            send(session, Map.of("type", "error", "message", "An error occurred processing your request."));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[ws] session closed: {}", session.getAttributes().get(SESSION_ID));
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                }
            }
        } catch (Exception e) {
            log.warn("[ws] failed to send frame: {}", e.getMessage());
        }
    }
}
