package com.rance.galcommunity.identity.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rance.galcommunity.identity.security.AppProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderTest {

    @Test
    void mapsMessageFields() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AppProperties props = new AppProperties(
                "http://localhost:5173",
                List.of("http://localhost:5173"),
                List.of("127.0.0.1/32"),
                "",
                new AppProperties.Jwt("", "", "", "", "gal-community",
                        Duration.ofMinutes(30), Duration.ofDays(7), false),
                new AppProperties.Cookie(false),
                new AppProperties.Email(true, 5000, "no-reply@example.com"));
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, props);

        sender.send("player@example.com", "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@example.com");
        assertThat(message.getTo()).containsExactly("player@example.com");
        assertThat(message.getSubject()).isEqualTo("Subject");
        assertThat(message.getText()).isEqualTo("Body");
    }
}
