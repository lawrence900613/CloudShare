package com.example.fileshare.share;

import com.example.fileshare.dto.ShareDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final ShareService shareService;
    private final String publicBaseUrl;

    public ShareController(
            ShareService shareService,
            @org.springframework.beans.factory.annotation.Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.shareService = shareService;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @PostMapping
    public ShareDTO.ShareResponse create(
            Authentication authentication,
            @Validated @RequestBody ShareDTO.CreateRequest request
    ) {
        ShareLink link = shareService.create(authentication.getName(), request);
        return new ShareDTO.ShareResponse(link, buildPublicDownloadUrl(link.getToken()));
    }

    @GetMapping
    public List<ShareDTO.ShareResponse> list(Authentication authentication) {
        return shareService.list(authentication.getName())
                .stream()
                .map(link -> new ShareDTO.ShareResponse(link, buildPublicDownloadUrl(link.getToken())))
                .toList();
    }

    @DeleteMapping("/{id}")
    public void revoke(Authentication authentication, @PathVariable Long id) {
        shareService.revoke(authentication.getName(), id);
    }

    @GetMapping("/public/{token}/download")
    public ResponseEntity<byte[]> downloadByShareLink(@PathVariable String token) {
        ShareService.DownloadPayload payload = shareService.downloadFromToken(token);
        String contentType = payload.contentType() == null || payload.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : payload.contentType();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(payload.bytes());
    }

    @GetMapping("/public/{token}")
    public ShareDTO.PublicShareResponse getPublicShare(@PathVariable String token) {
        ShareDTO.PublicShareResponse response = shareService.getPublicShare(token);
        response.publicDownloadUrl = buildPublicDownloadUrl(token);
        return response;
    }

    private String buildPublicDownloadUrl(String token) {
        return publicBaseUrl + "/api/shares/public/" + token + "/download";
    }
}
