package com.example.fileshare.share;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SharePublicController {

    private final ShareService shareService;

    public SharePublicController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/s/{token}")
    public ResponseEntity<byte[]> downloadByShortShareLink(@PathVariable String token) {
        ShareService.DownloadPayload payload = shareService.downloadFromToken(token);
        String contentType = payload.contentType() == null || payload.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : payload.contentType();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(payload.bytes());
    }
}
