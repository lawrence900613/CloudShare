package com.example.fileshare.request;


import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.fileshare.repository.UserRepository;
import com.example.fileshare.user.User;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RequestService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtHelper jwt;

    public RequestService(
            UserRepository users,
            PasswordEncoder encoder,
            JwtHelper jwt
    ) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
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
        u.setEmailVerified(true);
        users.save(u);

        return false;
    }

    public String login(String email, String rawPassword) {
        User u = users.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Invalid credentials"));
        if (!encoder.matches(rawPassword, u.getPasswordHashed())) {
            throw new ResponseStatusException(NOT_FOUND, "Invalid credentials");
        }
        return jwt.generateToken(u.getEmail());
    }

    @Transactional
    public void verifyEmail(String token) {
        // Email verification temporarily disabled.
    }

    @Transactional
    public boolean resendVerification(String email) {
        users.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
        // Email verification temporarily disabled.
        return false;
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
