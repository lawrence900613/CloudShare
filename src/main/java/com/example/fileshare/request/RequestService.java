package com.example.fileshare.request;


import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.fileshare.repository.UserRepository;
import com.example.fileshare.user.User;

@Service
public class RequestService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtHelper jwt;

    public RequestService(UserRepository users, PasswordEncoder encoder, JwtHelper jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public void register(String email, String rawPassword) {
        if (users.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        User u = new User();
        u.setEmail(email.toLowerCase());
        u.setPasswordHashed(encoder.encode(rawPassword));
        users.save(u);
    }

    public String login(String email, String rawPassword) {
        User u = users.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!encoder.matches(rawPassword, u.getPasswordHashed())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return jwt.generateToken(u.getEmail());
    }
}