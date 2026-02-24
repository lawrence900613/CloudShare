package com.example.fileshare.share;

import com.example.fileshare.dto.ShareDTO;
import com.example.fileshare.file.FileObject;
import com.example.fileshare.file.FileObjectRepository;
import com.example.fileshare.repository.UserRepository;
import com.example.fileshare.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ShareService {

    private final ShareLinkRepository shareLinks;
    private final FileObjectRepository files;
    private final UserRepository users;
    private final S3Client s3Client;
    private final String bucketName;

    public ShareService(
            ShareLinkRepository shareLinks,
            FileObjectRepository files,
            UserRepository users,
            S3Client s3Client,
            @org.springframework.beans.factory.annotation.Value("${app.aws.s3.bucket}") String bucketName
    ) {
        this.shareLinks = shareLinks;
        this.files = files;
        this.users = users;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    private static final int MAX_SHARES_PER_USER = 3;

    @Transactional
    public ShareLink create(String ownerEmail, ShareDTO.CreateRequest request) {
        Long ownerId = requireUserIdByEmail(ownerEmail);
        String key = request.key.trim();
        if (!key.startsWith(userPrefix(ownerEmail))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only share your own files");
        }

        // Replace any existing shares for the same file (dedup)
        shareLinks.deleteAllByOwnerIdAndS3Key(ownerId, key);

        // Enforce max 3 active share links
        long activeCount = shareLinks.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .filter(l -> !l.isRevoked())
                .filter(l -> l.getExpiresAt() == null || l.getExpiresAt().isAfter(Instant.now()))
                .count();
        if (activeCount >= MAX_SHARES_PER_USER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Share link limit reached (max " + MAX_SHARES_PER_USER + ")");
        }

        ShareLink link = new ShareLink();
        link.setOwnerId(ownerId);
        link.setS3Key(key);
        link.setToken(UUID.randomUUID().toString().replace("-", ""));
        if (request.expiresInMinutes != null) {
            link.setExpiresAt(Instant.now().plus(request.expiresInMinutes, ChronoUnit.MINUTES));
        }
        return shareLinks.save(link);
    }

    public List<ShareLink> list(String ownerEmail) {
        Long ownerId = requireUserIdByEmail(ownerEmail);
        return shareLinks.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Transactional
    public void revoke(String ownerEmail, Long id) {
        Long ownerId = requireUserIdByEmail(ownerEmail);
        ShareLink link = shareLinks.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found"));
        shareLinks.delete(link);
    }

    @Transactional
    public DownloadPayload downloadFromToken(String token) {
        ShareLink link = shareLinks.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid share link"));
        assertShareIsDownloadable(link);

        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(link.getS3Key())
                            .build()
            );
            link.setDownloadCount(link.getDownloadCount() + 1);
            shareLinks.save(link);
            String fileName = link.getS3Key().substring(link.getS3Key().lastIndexOf('/') + 1);
            String contentType = object.response().contentType();
            return new DownloadPayload(object.asByteArray(), fileName, contentType);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shared file not found", e);
        }
    }

    public ShareDTO.PublicShareResponse getPublicShare(String token) {
        ShareLink link = shareLinks.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid share link"));
        assertShareIsDownloadable(link);

        FileObject file = files.findByOwnerIdAndS3Key(link.getOwnerId(), link.getS3Key()).orElse(null);
        ShareDTO.PublicShareResponse response = new ShareDTO.PublicShareResponse();
        response.key = link.getS3Key();
        response.token = link.getToken();
        response.fileName = file != null ? file.getOriginalName() : extractFileName(link.getS3Key());
        response.mimeType = file != null ? file.getMimeType() : null;
        response.sizeBytes = file != null ? file.getSizeBytes() : 0;
        response.createdAt = link.getCreatedAt();
        response.expiresAt = link.getExpiresAt();
        response.downloadCount = link.getDownloadCount();
        response.revoked = link.isRevoked();
        return response;
    }

    private Long requireUserIdByEmail(String email) {
        User user = users.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return user.getId();
    }

    private String userPrefix(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        if (normalized.isBlank()) {
            normalized = "anonymous";
        }
        return normalized + "/";
    }

    private void assertShareIsDownloadable(ShareLink link) {
        if (link.isRevoked()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Share link revoked");
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Share link expired");
        }
    }

    private String extractFileName(String key) {
        if (key == null || key.isBlank()) {
            return "file";
        }
        int idx = key.lastIndexOf('/');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    public record DownloadPayload(byte[] bytes, String fileName, String contentType) {}
}
