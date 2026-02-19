package com.example.fileshare.request;
import com.example.fileshare.dto.RequestDTO.*;
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
    public RegisterResponseDTO register(@RequestBody RegisterDTO req) {
        boolean sent = service.register(req.email, req.password);
        if (sent) {
            return new RegisterResponseDTO("Registration successful. Verification email sent.", true);
        }
        return new RegisterResponseDTO("Registration successful. Email sending disabled; check backend logs for verification URL.", false);
    }

    @PostMapping("/login")
    public TokenDTO login(@RequestBody LoginDTO req) {
        String token = service.login(req.email, req.password);
        return new TokenDTO(token);
    }

    @GetMapping("/verify")
    public MessageDTO verify(@RequestParam String token) {
        service.verifyEmail(token);
        return new MessageDTO("Email verified successfully. You can now login.");
    }

    @PostMapping("/resend-verification")
    public RegisterResponseDTO resendVerification(@RequestBody RegisterDTO req) {
        boolean sent = service.resendVerification(req.email);
        if (sent) {
            return new RegisterResponseDTO("Verification email sent.", true);
        }
        return new RegisterResponseDTO("Email sending disabled; check backend logs for verification URL.", false);
    }
}
