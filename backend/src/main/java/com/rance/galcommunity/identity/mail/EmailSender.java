package com.rance.galcommunity.identity.mail;

public interface EmailSender {
    void send(String to, String subject, String body);
}
