package com.example.fileshare.request;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.fileshare.repository.UserRepository;
import com.example.fileshare.user.User;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class RequestService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final UserRepository users;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final VerificationMailService verificationMailService;
    private final PasswordEncoder encoder;
    private final JwtHelper jwt;
    private final long verificationTokenTtlMinutes;
    private final String verificationBaseUrl;
    private final long passwordResetTokenTtlMinutes;
    private final String passwordResetBaseUrl;

    public RequestService(
            UserRepository users,
            EmailVerificationTokenRepository verificationTokens,
            PasswordResetTokenRepository passwordResetTokens,
            VerificationMailService verificationMailService,
            PasswordEncoder encoder,
            JwtHelper jwt,
            @Value("${app.verification.token-ttl-minutes}") long verificationTokenTtlMinutes,
            @Value("${app.verification.base-url}") String verificationBaseUrl,
            @Value("${app.password-reset.token-ttl-minutes}") long passwordResetTokenTtlMinutes,
            @Value("${app.password-reset.base-url}") String passwordResetBaseUrl
    ) {
        this.users = users;
        this.verificationTokens = verificationTokens;
        this.passwordResetTokens = passwordResetTokens;
        this.verificationMailService = verificationMailService;
        this.encoder = encoder;
        this.jwt = jwt;
        this.verificationTokenTtlMinutes = verificationTokenTtlMinutes;
        this.verificationBaseUrl = verificationBaseUrl;
        this.passwordResetTokenTtlMinutes = passwordResetTokenTtlMinutes;
        this.passwordResetBaseUrl = passwordResetBaseUrl;
    }

    @Transactional
    public boolean register(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new ResponseStatusException(BAD_REQUEST, "Password must be at least 6 characters");
        }

        User user = users.findByEmail(normalizedEmail).orElse(null);
        if (user != null && user.isEmailVerified()) {
            throw new ResponseStatusException(CONFLICT, "Account existed.");
        }

        if (user == null) {
            user = new User();
            user.setEmail(normalizedEmail);
            user.setEmailVerified(false);
        }

        user.setPasswordHashed(encoder.encode(rawPassword));
        user.setEmailVerified(false);
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException | CannotAcquireLockException ex) {
            User existing = users.findByEmail(normalizedEmail)
                    .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Account existed."));
            if (existing.isEmailVerified()) {
                throw new ResponseStatusException(CONFLICT, "Account existed.");
            }
            existing.setPasswordHashed(encoder.encode(rawPassword));
            existing.setEmailVerified(false);
            user = users.saveAndFlush(existing);
        }

        EmailVerificationToken verificationToken = issueVerificationToken(user.getId());
        String verifyUrl = buildVerificationUrl(verificationToken.getToken());
        boolean canSend = verificationMailService.canSend();
        if (canSend) {
            verificationMailService.sendVerificationMailAsync(normalizedEmail, verifyUrl);
        }
        return canSend;
    }

    public String login(String email, String rawPassword) {
        User u = users.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Account not found."));
        if (!encoder.matches(rawPassword, u.getPasswordHashed())) {
            throw new ResponseStatusException(NOT_FOUND, "Password not correct.");
        }
        if (!u.isEmailVerified()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Email is not verified.");
        }
        return jwt.generateToken(u.getEmail());
    }

    @Transactional
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Verification token is required.");
        }

        EmailVerificationToken verificationToken = verificationTokens.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid verification token."));

        if (verificationToken.getConsumedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Verification token already used.");
        }

        if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(BAD_REQUEST, "Verification link expired. Please register again.");
        }

        User user = users.findById(verificationToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found."));

        user.setEmailVerified(true);
        users.save(user);

        verificationToken.setConsumedAt(Instant.now());
        verificationTokens.save(verificationToken);
    }

    @Transactional
    public boolean resendVerification(String email) {
        User user = users.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
        if (user.isEmailVerified()) {
            return false;
        }

        EmailVerificationToken verificationToken = issueVerificationToken(user.getId());
        String verifyUrl = buildVerificationUrl(verificationToken.getToken());
        boolean canSend = verificationMailService.canSend();
        if (canSend) {
            verificationMailService.sendVerificationMailAsync(user.getEmail(), verifyUrl);
        }
        return canSend;
    }

    @Transactional
    public boolean forgotPassword(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = users.findByEmail(normalizedEmail).orElse(null);
        boolean canSend = verificationMailService.canSend();
        if (user == null || !canSend) {
            return canSend;
        }

        PasswordResetToken token = issuePasswordResetToken(user.getId());
        String resetUrl = buildPasswordResetUrl(token.getToken());
        verificationMailService.sendPasswordResetMailAsync(normalizedEmail, resetUrl);
        return true;
    }

    @Transactional
    public void resetPassword(String token, String rawPassword) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token is required.");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new ResponseStatusException(BAD_REQUEST, "Password must be at least 6 characters");
        }

        PasswordResetToken resetToken = passwordResetTokens.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid password reset token."));

        if (resetToken.getConsumedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Password reset token already used.");
        }

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset link expired. Please request a new one.");
        }

        User user = users.findById(resetToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found."));

        user.setPasswordHashed(encoder.encode(rawPassword));
        users.save(user);

        resetToken.setConsumedAt(Instant.now());
        passwordResetTokens.save(resetToken);
    }

    private EmailVerificationToken issueVerificationToken(Long userId) {
        if (verificationTokenTtlMinutes <= 0) {
            throw new ResponseStatusException(BAD_GATEWAY, "Verification token TTL must be positive.");
        }

        verificationTokens.deleteAllByUserIdAndConsumedAtIsNull(userId);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(userId);
        token.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        token.setExpiresAt(Instant.now().plus(verificationTokenTtlMinutes, ChronoUnit.MINUTES));
        return verificationTokens.save(token);
    }

    private String buildVerificationUrl(String token) {
        String normalizedBase = verificationBaseUrl == null ? "" : verificationBaseUrl.trim();
        if (normalizedBase.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Verification base URL is not configured.");
        }
        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return normalizedBase + "/verify-email?token=" + encoded;
    }

    private PasswordResetToken issuePasswordResetToken(Long userId) {
        if (passwordResetTokenTtlMinutes <= 0) {
            throw new ResponseStatusException(BAD_GATEWAY, "Password reset token TTL must be positive.");
        }

        passwordResetTokens.deleteAllByUserIdAndConsumedAtIsNull(userId);

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        token.setExpiresAt(Instant.now().plus(passwordResetTokenTtlMinutes, ChronoUnit.MINUTES));
        return passwordResetTokens.save(token);
    }

    private String buildPasswordResetUrl(String token) {
        String normalizedBase = passwordResetBaseUrl == null ? "" : passwordResetBaseUrl.trim();
        if (normalizedBase.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Password reset base URL is not configured.");
        }
        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return normalizedBase + "/reset-password?token=" + encoded;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is required");
        }
        String normalized = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "Email must be valid");
        }
        return normalized;
    }
}
