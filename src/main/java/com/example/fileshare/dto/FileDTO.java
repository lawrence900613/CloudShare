package com.example.fileshare.dto;

import com.example.fileshare.file.FileObject;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class FileDTO {

    public static class CreateRequest {
        @NotBlank
        public String originalName;
        public String mimeType;
        @Min(0)
        public long sizeBytes;
    }

    public static class UpdateRequest {
        @NotBlank
        public String originalName;
    }

    public static class DirectUploadRequest {
        @NotBlank
        public String originalName;
        public String mimeType;
        @Min(0)
        public long sizeBytes;
    }

    public static class DirectUploadCompleteRequest {
        @NotBlank
        public String s3Key;
        @NotBlank
        public String originalName;
        public String mimeType;
        @NotNull
        @Min(0)
        public Long sizeBytes;
    }

    public static class DirectUploadUrlResponse {
        public String s3Key;
        public String uploadUrl;
        public String method;
        public long expiresInSeconds;

        public DirectUploadUrlResponse(String s3Key, String uploadUrl, String method, long expiresInSeconds) {
            this.s3Key = s3Key;
            this.uploadUrl = uploadUrl;
            this.method = method;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    public static class FileResponse {
        public Long id;
        public String s3Key;
        public String originalName;
        public String mimeType;
        public long sizeBytes;
        public Instant createdAt;
        public String url;

        public FileResponse(FileObject file, String bucketName) {
            this.id = file.getId();
            this.s3Key = file.getS3Key();
            this.originalName = file.getOriginalName();
            this.mimeType = file.getMimeType();
            this.sizeBytes = file.getSizeBytes();
            this.createdAt = file.getCreatedAt();
            this.url = "https://" + bucketName + ".s3.amazonaws.com/" + file.getS3Key();
        }
    }

    public static class S3ObjectResponse {
        public String key;
        public String fileName;
        public long sizeBytes;
        public Instant lastModified;
        public String url;

        public S3ObjectResponse(String key, long sizeBytes, Instant lastModified, String bucketName) {
            this.key = key;
            this.fileName = key == null ? "" : key.substring(key.lastIndexOf('/') + 1);
            this.sizeBytes = sizeBytes;
            this.lastModified = lastModified;
            this.url = "https://" + bucketName + ".s3.amazonaws.com/" + key;
        }
    }
}
