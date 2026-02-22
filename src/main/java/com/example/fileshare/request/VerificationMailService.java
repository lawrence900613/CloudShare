package com.example.fileshare.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class VerificationMailService {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailService.class);

    private final JavaMailSender mailSender;
    @Value("${spring.mail.username}")
    private String from;

    public VerificationMailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    public boolean canSend() {
        return mailSender != null && from != null && !from.isBlank();
    }

    @Async
    public void sendVerificationMailAsync(String toEmail, String verifyUrl) {
        if (from == null || from.isBlank()) {
            log.info("Mail sender username is empty. Verification URL for {}: {}", toEmail, verifyUrl);
            return;
        }

        String body = "Welcome to FileShare.\n\n" +
                "Please verify your email by opening this link:\n" +
                verifyUrl + "\n\n" +
                "If you did not sign up, you can ignore this email.";

        try {
            if (mailSender == null) {
                log.warn("SMTP mail sender is not configured. Verification URL for {}: {}", toEmail, verifyUrl);
                return;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("Verify your FileShare account");
            message.setText(body);
            mailSender.send(message);
            log.info("Verification email sent to {}", toEmail);
        } catch (Exception ex) {
            log.error("Could not send verification email to {}: {}", toEmail, ex.getMessage());
        }
    }

    @Async
    public void sendPasswordResetMailAsync(String toEmail, String resetUrl) {
        if (from == null || from.isBlank()) {
            log.info("Mail sender username is empty. Reset URL for {}: {}", toEmail, resetUrl);
            return;
        }

        String body = "We received a password reset request for your FileShare account.\n\n" +
                "Reset your password using this link:\n" +
                resetUrl + "\n\n" +
                "If you did not request this, you can ignore this email.";

        try {
            if (mailSender == null) {
                log.warn("SMTP mail sender is not configured. Reset URL for {}: {}", toEmail, resetUrl);
                return;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("Reset your FileShare password");
            message.setText(body);
            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception ex) {
            log.error("Could not send password reset email to {}: {}", toEmail, ex.getMessage());
        }
    }
}
