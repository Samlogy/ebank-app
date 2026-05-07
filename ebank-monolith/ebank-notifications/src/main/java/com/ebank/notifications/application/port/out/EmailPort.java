package com.ebank.notifications.application.port.out;

public interface EmailPort {
    void send(String to, String subject, String body);
}
