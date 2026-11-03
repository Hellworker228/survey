package org.example.survey.service;

import org.example.survey.config.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final AppSettings settings;

    public MailService(JavaMailSender mailSender, AppSettings settings) {
        this.mailSender = mailSender;
        this.settings = settings;
    }

    public boolean send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(settings.getMailFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            return true;
        } catch (RuntimeException e) {
            log.error("Failed to send mail to {}", to, e);
            return false;
        }
    }
}
