package com.example.fileshare.request;


import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.fileshare.repository.UserRepository;
import com.example.fileshare.user.User;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RequestService {

    private final UserRepository users;
    private final EmailVerificationTokenRepository verificationTokens;
    private final VerificationMailService verificationMailService;
    private final PasswordEncoder encoder;
    private final JwtHelper jwt;
    private final String verificationBaseUrl;

    public RequestService(
            UserRepository users,
            EmailVerificationTokenRepository verificationTokens,
            VerificationMailService verificationMailService,
            PasswordEncoder encoder,
            JwtHelper jwt,
            @org.springframework.beans.factory.annotation.Value("${app.verification.base-url:http://localhost:5173}") String verificationBaseUrl
    ) {
        this.users = users;
        this.verificationTokens = verificationTokens;
        this.verificationMailService = verificationMailService;
        this.encoder = encoder;
        this.jwt = jwt;
        this.verificationBaseUrl = verificationBaseUrl.endsWith("/")
                ? verificationBaseUrl.substring(0, verificationBaseUrl.length() - 1)
                : verificationBaseUrl;
    }

    @Transactional
    public boolean register(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new ResponseStatusException(BAD_REQUEST, "Password must be at least 6 characters");
        }

        if (users.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(CONFLICT, "Email already registered");
        }

        User u = new User();
        u.setEmail(normalizedEmail);
        u.setPasswordHashed(encoder.encode(rawPassword));
        u.setEmailVerified(false);
        users.save(u);

        EmailVerificationToken token = createToken(u.getId());
        String verifyUrl = verificationBaseUrl + "/verify-email?token=" + token.getToken();
        return verificationMailService.sendVerificationMail(u.getEmail(), verifyUrl);
    }

    public String login(String email, String rawPassword) {
        User u = users.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Invalid credentials"));
        if (!encoder.matches(rawPassword, u.getPasswordHashed())) {
            throw new ResponseStatusException(NOT_FOUND, "Invalid credentials");
        }
        if (!u.isEmailVerified()) {
            throw new ResponseStatusException(FORBIDDEN, "Please verify your email before login");
        }
        return jwt.generateToken(u.getEmail());
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = verificationTokens.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid verification token"));

        if (verificationToken.getConsumedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Verification token already used");
        }
        if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(BAD_REQUEST, "Verification token has expired");
        }

        User user = users.findById(verificationToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "User for this token was not found"));

        user.setEmailVerified(true);
        users.save(user);

        verificationToken.setConsumedAt(Instant.now());
        verificationTokens.save(verificationToken);
        verificationTokens.deleteAllByUserIdAndConsumedAtIsNull(user.getId());
    }

    @Transactional
    public boolean resendVerification(String email) {
        User user = users.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        if (user.isEmailVerified()) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is already verified");
        }

        verificationTokens.deleteAllByUserIdAndConsumedAtIsNull(user.getId());
        EmailVerificationToken token = createToken(user.getId());
        String verifyUrl = verificationBaseUrl + "/verify-email?token=" + token.getToken();
        return verificationMailService.sendVerificationMail(user.getEmail(), verifyUrl);
    }

    private EmailVerificationToken createToken(Long userId) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(userId);
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        return verificationTokens.save(token);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is required");
        }
        return email.trim().toLowerCase();
    }
}
