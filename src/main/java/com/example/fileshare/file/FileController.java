package com.example.fileshare.file;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public FileService.UploadResponse upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String owner = authentication != null ? authentication.getName() : "anonymous";
        return fileService.upload(file, owner);
    }
}
