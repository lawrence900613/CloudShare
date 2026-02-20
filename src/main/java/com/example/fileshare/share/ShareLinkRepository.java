package com.example.fileshare.share;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
    Optional<ShareLink> findByToken(String token);
    Optional<ShareLink> findByIdAndOwnerId(Long id, Long ownerId);
    List<ShareLink> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    void deleteAllByOwnerIdAndS3Key(Long ownerId, String s3Key);
}
