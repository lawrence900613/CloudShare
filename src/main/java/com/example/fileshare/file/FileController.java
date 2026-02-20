package com.example.fileshare.file;

import com.example.fileshare.dto.FileDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    /**
     * Creates the controller with the file service dependency used by all endpoints.
     * This keeps HTTP handling thin and delegates business logic to the service layer.
     */
    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Creates a new file record for the authenticated user using the request payload.
     * Returns the saved file metadata in API response format.
     */
    @PostMapping
    public FileDTO.FileResponse create(
            Authentication authentication,
            @Validated @RequestBody FileDTO.CreateRequest request
    ) {
        FileObject saved = fileService.create(authentication.getName(), request);
        return new FileDTO.FileResponse(saved, fileService.getBucketName());
    }

    /**
     * Generates a presigned URL for direct client-to-S3 upload.
     * The client uploads with the returned key and then calls the completion endpoint.
     */
    @PostMapping("/upload/presigned")
    public FileDTO.DirectUploadUrlResponse createPresignedUploadUrl(
            Authentication authentication,
            @Validated @RequestBody FileDTO.DirectUploadRequest request
    ) {
        return fileService.createDirectUploadUrl(authentication.getName(), request);
    }

    /**
     * Completes a direct upload by persisting metadata for a verified S3 object.
     * Returns the saved file metadata in the standard API response format.
     */
    @PostMapping("/upload/complete")
    public FileDTO.FileResponse completePresignedUpload(
            Authentication authentication,
            @Validated @RequestBody FileDTO.DirectUploadCompleteRequest request
    ) {
        FileObject saved = fileService.completeDirectUpload(authentication.getName(), request);
        return new FileDTO.FileResponse(saved, fileService.getBucketName());
    }

    /**
     * Returns a paginated list of file records owned by the authenticated user.
     * Each item is mapped to the API response DTO including the bucket context.
     */
    @GetMapping
    public List<FileDTO.FileResponse> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return fileService.list(authentication.getName(), page, size)
                .stream()
                .map(file -> new FileDTO.FileResponse(file, fileService.getBucketName()))
                .toList();
    }

    /**
     * Returns one file record owned by the authenticated user by id.
     * The response includes metadata and bucket context for client usage.
     */
    @GetMapping("/{id}")
    public FileDTO.FileResponse get(
            Authentication authentication,
            @PathVariable Long id
    ) {
        FileObject file = fileService.getOne(authentication.getName(), id);
        return new FileDTO.FileResponse(file, fileService.getBucketName());
    }

    /**
     * Renames a file record owned by the authenticated user.
     * Returns the updated file metadata after persisting the new name.
     */
    @PatchMapping("/{id}")
    public FileDTO.FileResponse rename(
            Authentication authentication,
            @PathVariable Long id,
            @Validated @RequestBody FileDTO.UpdateRequest request
    ) {
        FileObject file = fileService.updateName(authentication.getName(), id, request);
        return new FileDTO.FileResponse(file, fileService.getBucketName());
    }

    /**
     * Deletes a file record and associated storage object for the authenticated user.
     * This endpoint returns no body on success.
     */
    @DeleteMapping("/{id}")
    public void delete(
            Authentication authentication,
            @PathVariable Long id
    ) {
        fileService.delete(authentication.getName(), id);
    }

    /**
     * Lists S3 objects that belong to the authenticated user prefix.
     * Results are returned as object metadata DTOs.
     */
    @GetMapping("/s3")
    public List<FileDTO.S3ObjectResponse> listS3(Authentication authentication) {
        return fileService.listS3Files(authentication.getName());
    }

    /**
     * Deletes one S3 object owned by the authenticated user by key.
     * This also clears related share link metadata when present.
     */
    @DeleteMapping("/s3")
    public void deleteFromS3(
            Authentication authentication,
            @RequestParam String key
    ) {
        fileService.deleteS3File(authentication.getName(), key);
    }

    /**
     * Downloads one user owned S3 object and returns it as an attachment response.
     * Content type falls back to octet-stream when missing from object metadata.
     */
    @GetMapping("/s3/download")
    public ResponseEntity<byte[]> downloadFromS3(
            Authentication authentication,
            @RequestParam String key
    ) {
        FileService.DownloadPayload payload = fileService.downloadS3File(authentication.getName(), key);
        String contentType = payload.contentType() == null || payload.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : payload.contentType();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(payload.bytes());
    }

    /**
     * Uploads a multipart file to S3 for the current authenticated user.
     * Returns the persisted file record metadata after a successful upload.
     */
    @Deprecated(forRemoval = false)
    @PostMapping("/upload")
    public FileDTO.FileResponse upload(
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        String owner = authentication != null ? authentication.getName() : "anonymous";
        FileObject saved = fileService.upload(file, owner);
        return new FileDTO.FileResponse(saved, fileService.getBucketName());
    }
}
