package com.example.fileshare.file;

import com.example.fileshare.dto.FileDTO;
import com.example.fileshare.repository.UserRepository;
import com.example.fileshare.share.ShareLinkRepository;
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
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileService {

    private final FileObjectRepository files;
    private final UserRepository users;
    private final ShareLinkRepository shareLinks;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;
    private final int maxFilesPerUser;
    private final long presignPutTtlMinutes;

    /**
     * Wires repository and S3 dependencies and loads file-related configuration.
     * These values drive storage operations and per-user limits.
     */
    public FileService(
            FileObjectRepository files,
            UserRepository users,
            ShareLinkRepository shareLinks,
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${app.aws.s3.bucket}") String bucketName,
            @Value("${app.files.max-per-user}") int maxFilesPerUser,
            @Value("${app.aws.s3.presign-put-ttl-minutes}") long presignPutTtlMinutes
    ) {
        this.files = files;
        this.users = users;
        this.shareLinks = shareLinks;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
        this.maxFilesPerUser = maxFilesPerUser;
        this.presignPutTtlMinutes = presignPutTtlMinutes;
    }

    /**
     * Returns a paginated list of file metadata for the given user email.
     * Results are ordered from newest to oldest by creation time.
     */
    public List<FileObject> list(String email, int page, int size) {
        Long ownerId = requireUserIdByEmail(email);
        return files.findAllByOwnerIdOrderByCreatedAtDesc(ownerId, PageRequest.of(page, size));
    }

    /**
     * Creates a metadata record with a generated owner-scoped S3 key.
     * This does not upload bytes and is used for metadata-only creation flows.
     */
    @Transactional
    public FileObject create(String ownerEmail, FileDTO.CreateRequest req) {
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

    /**
     * Generates a short-lived presigned PUT URL so the client uploads directly to S3.
     * The returned key is owner-scoped and must be used in the completion request.
     */
    public FileDTO.DirectUploadUrlResponse createDirectUploadUrl(String ownerEmail, FileDTO.DirectUploadRequest req) {
        Long ownerId = requireUserIdByEmail(ownerEmail);
        enforceUserFileLimit(ownerId);

        String ownerFolder = normalizeOwner(ownerEmail);
        String cleanName = req.originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = ownerFolder + "/" + UUID.randomUUID() + "/" + cleanName;
        String contentType = (req.mimeType == null || req.mimeType.isBlank())
                ? "application/octet-stream"
                : req.mimeType;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(presignPutTtlMinutes))
                        .putObjectRequest(putObjectRequest)
                        .build()
        );

        return new FileDTO.DirectUploadUrlResponse(
                key,
                presigned.url().toString(),
                "PUT",
                presignPutTtlMinutes * 60
        );
    }

    /**
     * Finalizes a direct upload by verifying the S3 object exists and saving metadata.
     * This keeps DB rows consistent with objects the user actually uploaded.
     */
    @Transactional
    public FileObject completeDirectUpload(String ownerEmail, FileDTO.DirectUploadCompleteRequest req) {
        Long ownerId = requireUserIdByEmail(ownerEmail);
        enforceUserFileLimit(ownerId);

        String prefix = userPrefix(ownerEmail);
        if (req.s3Key == null || req.s3Key.isBlank() || !req.s3Key.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only register your own uploaded files");
        }

        if (files.findByOwnerIdAndS3Key(ownerId, req.s3Key).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "File metadata already exists for this S3 key");
        }

        HeadObjectResponse existingObject;
        try {
            existingObject = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(req.s3Key)
                    .build());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Uploaded object not found in S3", e);
        }

        String resolvedMimeType = (req.mimeType == null || req.mimeType.isBlank())
                ? existingObject.contentType()
                : req.mimeType;
        long resolvedSizeBytes = existingObject.contentLength() == null ? req.sizeBytes : existingObject.contentLength();

        FileObject object = new FileObject();
        object.setOwnerId(ownerId);
        object.setS3Key(req.s3Key);
        object.setOriginalName(req.originalName);
        object.setMimeType(resolvedMimeType);
        object.setSizeBytes(resolvedSizeBytes);
        return files.save(object);
    }

    /**
     * Returns one file owned by the given user email and id.
     * Throws NOT_FOUND if the record is missing or belongs to another user.
     */
    public FileObject getOne(String ownerEmail, Long id) {
        Long ownerId = requireUserIdByEmail(ownerEmail);
        return files.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    /**
     * Renames a user-owned file by copying the S3 object to a new key and deleting the old key.
     * Related metadata and share links are updated to point to the new key.
     */
    @Transactional
    public FileObject updateName(String ownerEmail, Long id, FileDTO.UpdateRequest req) {
        FileObject file = getOne(ownerEmail, id);
        String oldKey = file.getS3Key();
        int lastSlash = oldKey.lastIndexOf('/');
        String keyPrefix = lastSlash >= 0 ? oldKey.substring(0, lastSlash + 1) : "";
        String cleanName = req.originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String newKey = keyPrefix + cleanName;

        if (!newKey.equals(oldKey)) {
            if (files.findByOwnerIdAndS3Key(file.getOwnerId(), newKey).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A file with this name already exists");
            }

            try {
                s3Client.copyObject(CopyObjectRequest.builder()
                        .sourceBucket(bucketName)
                        .sourceKey(oldKey)
                        .destinationBucket(bucketName)
                        .destinationKey(newKey)
                        .metadataDirective("COPY")
                        .build());

                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(oldKey)
                        .build());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rename in S3 failed", e);
            }

            rekeyShareLinks(file.getOwnerId(), oldKey, newKey);
            file.setS3Key(newKey);
        }

        file.setOriginalName(req.originalName);
        return files.save(file);
    }

    /**
     * Deletes a user-owned file from S3 and then removes database metadata.
     * The S3 delete is kept idempotent so missing objects do not fail the request.
     */
    @Transactional
    public void delete(String ownerEmail, Long id) {
        FileObject file = getOne(ownerEmail, id);
        removeShareLinks(file.getOwnerId(), file.getS3Key());
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

    /**
     * Lists S3 objects under the current user's storage prefix.
     * Directory markers are filtered out before mapping to response DTOs.
     */
    public List<FileDTO.S3ObjectResponse> listS3Files(String ownerEmail) {
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

    /**
     * Deletes one S3 object for the user after prefix ownership validation.
     * Related share links and optional metadata rows are removed as cleanup.
     */
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

        removeShareLinks(ownerId, key);
        files.findByOwnerIdAndS3Key(ownerId, key).ifPresent(files::delete);
    }

    /**
     * Downloads one user-owned S3 object and returns bytes plus response metadata.
     * Access is denied when the key is outside the caller's prefix.
     */
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

    /**
     * Uploads a multipart file to S3 and persists its metadata for the owner.
     * The operation enforces per-user file limits before upload.
     */
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

    /**
     * Returns the configured S3 bucket name used by this service.
     * Controllers expose this to help clients build object context.
     */
    public String getBucketName() {
        return bucketName;
    }

    /**
     * Resolves a user id from email and fails with UNAUTHORIZED when missing.
     * Email is normalized to lowercase before lookup.
     */
    private Long requireUserIdByEmail(String email) {
        User user = users.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return user.getId();
    }

    /**
     * Normalizes an email into a safe folder segment for S3 keys.
     * Invalid characters are replaced and empty values map to "anonymous".
     */
    private String normalizeOwner(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        return normalized.isBlank() ? "anonymous" : normalized;
    }

    /**
     * Builds the S3 prefix used to scope object operations per user.
     * All ownership checks rely on this derived prefix.
     */
    private String userPrefix(String email) {
        return normalizeOwner(email) + "/";
    }

    /**
     * Enforces the configured maximum file count for one owner.
     * Throws CONFLICT when the account has reached its limit.
     */
    private void enforceUserFileLimit(Long ownerId) {
        long current = files.countByOwnerId(ownerId);
        if (current >= maxFilesPerUser) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "File limit reached (" + maxFilesPerUser + " per account)"
            );
        }
    }

    /**
     * Maps an AWS SDK S3 object into the API response DTO shape.
     * Bucket name is included so clients have full storage context.
     */
    private FileDTO.S3ObjectResponse mapS3Object(S3Object object) {
        return new FileDTO.S3ObjectResponse(
                object.key(),
                object.size(),
                object.lastModified(),
                bucketName
        );
    }

    /**
     * Removes all share links that reference one owner/key pair.
     * This keeps share metadata consistent after deletes.
     */
    private void removeShareLinks(Long ownerId, String key) {
        shareLinks.deleteAllByOwnerIdAndS3Key(ownerId, key);
    }

    private void rekeyShareLinks(Long ownerId, String oldKey, String newKey) {
        List<com.example.fileshare.share.ShareLink> links = shareLinks.findAllByOwnerIdAndS3Key(ownerId, oldKey);
        if (links.isEmpty()) {
            return;
        }

        List<com.example.fileshare.share.ShareLink> updated = new ArrayList<>(links.size());
        for (com.example.fileshare.share.ShareLink link : links) {
            link.setS3Key(newKey);
            updated.add(link);
        }
        shareLinks.saveAll(updated);
    }

    public record DownloadPayload(byte[] bytes, String fileName, String contentType) {}
}
