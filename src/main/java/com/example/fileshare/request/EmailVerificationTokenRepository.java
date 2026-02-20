package com.example.fileshare.request;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(Long userId);
    void deleteAllByUserIdAndConsumedAtIsNull(Long userId);
}
