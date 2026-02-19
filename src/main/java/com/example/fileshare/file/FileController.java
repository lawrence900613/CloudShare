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

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    public FileDTO.FileResponse create(
            Authentication authentication,
            @Validated @RequestBody FileDTO.CreateRequest request
    ) {
        FileObject saved = fileService.create(authentication.getName(), request);
        return new FileDTO.FileResponse(saved, fileService.getBucketName());
    }

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

    @GetMapping("/{id}")
    public FileDTO.FileResponse get(
            Authentication authentication,
            @PathVariable Long id
    ) {
        FileObject file = fileService.getOne(authentication.getName(), id);
        return new FileDTO.FileResponse(file, fileService.getBucketName());
    }

    @PatchMapping("/{id}")
    public FileDTO.FileResponse rename(
            Authentication authentication,
            @PathVariable Long id,
            @Validated @RequestBody FileDTO.UpdateRequest request
    ) {
        FileObject file = fileService.updateName(authentication.getName(), id, request);
        return new FileDTO.FileResponse(file, fileService.getBucketName());
    }

    @DeleteMapping("/{id}")
    public void delete(
            Authentication authentication,
            @PathVariable Long id
    ) {
        fileService.delete(authentication.getName(), id);
    }

    @GetMapping("/s3")
    public List<FileDTO.S3ObjectResponse> listS3(Authentication authentication) {
        return fileService.listS3Files(authentication.getName());
    }

    @DeleteMapping("/s3")
    public void deleteFromS3(
            Authentication authentication,
            @RequestParam String key
    ) {
        fileService.deleteS3File(authentication.getName(), key);
    }

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
