package com.example.fileshare.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FileService {

    private final S3Client s3Client;
    private final String bucketName;

    public FileService(
            @Value("${app.aws.region}") String region,
            @Value("${app.aws.s3.bucket}") String bucketName,
            @Value("${app.aws.access-key:}") String accessKey,
            @Value("${app.aws.secret-key:}") String secretKey
    ) {
        this.bucketName = bucketName;

        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        this.s3Client = builder.build();
    }

    public UploadResponse upload(MultipartFile file, String owner) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        String originalFilename = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String cleanName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "uploads/" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + cleanName;

        Map<String, String> metadata = new HashMap<>();
        metadata.put("uploaded-by", owner);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .metadata(metadata)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read uploaded file", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upload to S3 failed", e);
        }

        String fileUrl = "https://" + bucketName + ".s3.amazonaws.com/" + key;
        return new UploadResponse(bucketName, key, fileUrl, file.getSize());
    }

    public record UploadResponse(String bucket, String key, String url, long sizeBytes) {}
}
