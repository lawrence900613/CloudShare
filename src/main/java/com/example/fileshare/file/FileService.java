package com.example.fileshare.file;

import com.example.fileshare.dto.FileDtos;
import com.example.fileshare.repository.UserRepository;
import com.example.fileshare.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileService {

    private final FileObjectRepository files;
    private final UserRepository users;
    private final S3Client s3Client;
    private final String bucketName;
    private final int maxFilesPerUser;

    public FileService(
            FileObjectRepository files,
            UserRepository users,
            S3Client s3Client,
            @Value("${app.aws.s3.bucket}") String bucketName,
            @Value("${app.files.max-per-user:5}") int maxFilesPerUser
    ) {
        this.files = files;
        this.users = users;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.maxFilesPerUser = maxFilesPerUser;
    }

    public List<FileObject> list(String email, int page, int size) {
        Long ownerId = requireUserIdByEmail(email);
        return files.findAllByOwnerIdOrderByCreatedAtDesc(ownerId, PageRequest.of(page, size));
    }

    @Transactional
    public FileObject create(String ownerEmail, FileDtos.CreateRequest req) {
        Long ownerId = requireUserIdByEmail(ownerEmail);
        enforceUserFileLimit(ownerId);
        String ownerFolder = normalizeOwner(ownerEmail);
        String cleanName = req.originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = ownerFolder + "/" + UUID.randomUUID() + "/" + cleanName;

        FileObject object = new FileObject();
        object.setOwnerId(ownerId);
        object.setS3Key(key);
        object.setOriginalName(req.originalName);
        object.setMimeType(req.mimeType);
        object.setSizeBytes(req.sizeBytes);
        return files.save(object);
    }

    public FileObject getOne(String ownerEmail, Long id) {
        Long ownerId = requireUserIdByEmail(ownerEmail);
        return files.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    @Transactional
    public FileObject updateName(String ownerEmail, Long id, FileDtos.UpdateRequest req) {
        FileObject file = getOne(ownerEmail, id);
        file.setOriginalName(req.originalName);
        return files.save(file);
    }

    @Transactional
    public void delete(String ownerEmail, Long id) {
        FileObject file = getOne(ownerEmail, id);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(file.getS3Key())
                    .build());
        } catch (Exception ignored) {
            // Keep delete idempotent even if object is already missing in S3.
        }
        files.delete(file);
    }

    public List<FileDtos.S3ObjectResponse> listS3Files(String ownerEmail) {
        String prefix = userPrefix(ownerEmail);
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        return response.contents()
                .stream()
                .filter(object -> !object.key().endsWith("/"))
                .map(object -> mapS3Object(object))
                .toList();
    }

    @Transactional
    public void deleteS3File(String ownerEmail, String key) {
        Long ownerId = requireUserIdByEmail(ownerEmail);
        String prefix = userPrefix(ownerEmail);
        if (key == null || key.isBlank() || !key.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own files");
        }

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());

        files.findByOwnerIdAndS3Key(ownerId, key).ifPresent(files::delete);
    }

    public DownloadPayload downloadS3File(String ownerEmail, String key) {
        String prefix = userPrefix(ownerEmail);
        if (key == null || key.isBlank() || !key.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only download your own files");
        }

        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build()
            );
            String fileName = key.substring(key.lastIndexOf('/') + 1);
            String contentType = object.response().contentType();
            return new DownloadPayload(object.asByteArray(), fileName, contentType);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found in S3", e);
        }
    }

    @Transactional
    public FileObject upload(MultipartFile file, String ownerEmail) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        Long ownerId = requireUserIdByEmail(ownerEmail);
        enforceUserFileLimit(ownerId);
        String ownerFolder = normalizeOwner(ownerEmail);
        String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "file.bin");
        String cleanName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = ownerFolder + "/" + UUID.randomUUID() + "/" + cleanName;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upload to S3 failed", e);
        }

        FileObject object = new FileObject();
        object.setOwnerId(ownerId);
        object.setS3Key(key);
        object.setOriginalName(originalFilename);
        object.setMimeType(file.getContentType());
        object.setSizeBytes(file.getSize());
        return files.save(object);
    }

    public String getBucketName() {
        return bucketName;
    }

    private Long requireUserIdByEmail(String email) {
        User user = users.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return user.getId();
    }

    private String normalizeOwner(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        return normalized.isBlank() ? "anonymous" : normalized;
    }

    private String userPrefix(String email) {
        return normalizeOwner(email) + "/";
    }

    private void enforceUserFileLimit(Long ownerId) {
        long current = files.countByOwnerId(ownerId);
        if (current >= maxFilesPerUser) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "File limit reached (" + maxFilesPerUser + " per account)"
            );
        }
    }

    private FileDtos.S3ObjectResponse mapS3Object(S3Object object) {
        return new FileDtos.S3ObjectResponse(
                object.key(),
                object.size(),
                object.lastModified(),
                bucketName
        );
    }

    public record DownloadPayload(byte[] bytes, String fileName, String contentType) {}
}
