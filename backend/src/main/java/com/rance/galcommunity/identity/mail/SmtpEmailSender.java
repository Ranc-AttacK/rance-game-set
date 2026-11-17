package com.rance.galcommunity.identity.mail;

import com.rance.galcommunity.identity.security.AppProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final AppProperties props;

    public SmtpEmailSender(JavaMailSender mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.email().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
