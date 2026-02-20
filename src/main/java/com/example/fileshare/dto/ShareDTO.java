package com.example.fileshare.dto;

import com.example.fileshare.share.ShareLink;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public class ShareDTO {

    public static class CreateRequest {
        @NotBlank
        public String key;
        @Min(1)
        public Long expiresInMinutes;
    }

    public static class ShareResponse {
        public Long id;
        public String key;
        public String token;
        public String publicDownloadUrl;
        public Instant expiresAt;
        public boolean revoked;
        public Instant createdAt;
        public long downloadCount;

        public ShareResponse(ShareLink link, String publicDownloadUrl) {
            this.id = link.getId();
            this.key = link.getS3Key();
            this.token = link.getToken();
            this.publicDownloadUrl = publicDownloadUrl;
            this.expiresAt = link.getExpiresAt();
            this.revoked = link.isRevoked();
            this.createdAt = link.getCreatedAt();
            this.downloadCount = link.getDownloadCount();
        }
    }

    public static class PublicShareResponse {
        public String key;
        public String token;
        public String fileName;
        public String mimeType;
        public long sizeBytes;
        public Instant createdAt;
        public Instant expiresAt;
        public long downloadCount;
        public boolean revoked;
        public String publicDownloadUrl;

        public PublicShareResponse() {
        }
    }
}
