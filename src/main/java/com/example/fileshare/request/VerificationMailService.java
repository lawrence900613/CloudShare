package com.example.fileshare.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class VerificationMailService {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailService.class);

    private final JavaMailSender mailSender;
    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:no-reply@fileshare.local}")
    private String from;

    public VerificationMailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    public boolean sendVerificationMail(String toEmail, String verifyUrl) {
        if (!enabled) {
            log.info("Email disabled. Verification URL for {}: {}", toEmail, verifyUrl);
            return false;
        }

        String body = "Welcome to FileShare.\n\n" +
                "Please verify your email by opening this link:\n" +
                verifyUrl + "\n\n" +
                "If you did not sign up, you can ignore this email.";

        try {
            if (mailSender == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "SMTP mail sender is not configured");
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("Verify your FileShare account");
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Could not send verification email: " + ex.getMessage());
        }
    }
}
