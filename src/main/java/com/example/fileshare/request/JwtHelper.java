package com.example.fileshare.request;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtHelper {
    // Used for hashed the info
    private final SecretKey key;
    // Limited valid time of hashed info
    private final long validityInMilliseconds;

    public JwtHelper(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.ttlMinutes}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityInMilliseconds = ttlMinutes * 60_000;
    }

    public String generateToken(String subject) {
        long current = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date(current))
                .setExpiration(new Date(current + validityInMilliseconds))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String validateAndGetSubject(String token) {
        return Jwts.parser()
                .verifyWith(key) // key should be javax.crypto.SecretKey
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}