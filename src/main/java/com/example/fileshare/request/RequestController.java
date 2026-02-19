package com.example.fileshare.request;
import com.example.fileshare.dto.RequestDTO.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class RequestController {

    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public RegisterResponseDTO register(@Valid @RequestBody RegisterDTO req) {
        service.register(req.email, req.password);
        return new RegisterResponseDTO("Registration successful.", false);
    }

    @PostMapping("/login")
    public TokenDTO login(@Valid @RequestBody LoginDTO req) {
        String token = service.login(req.email, req.password);
        return new TokenDTO(token);
    }

    @GetMapping("/verify")
    public MessageDTO verify(@RequestParam String token) {
        service.verifyEmail(token);
        return new MessageDTO("Email verification is currently disabled.");
    }

    @PostMapping("/resend-verification")
    public RegisterResponseDTO resendVerification(@Valid @RequestBody ResendVerificationDTO req) {
        service.resendVerification(req.email);
        return new RegisterResponseDTO("Email verification is currently disabled.", false);
    }
}
