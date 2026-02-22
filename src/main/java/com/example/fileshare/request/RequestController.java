package com.example.fileshare.request;
import com.example.fileshare.dto.RequestDTO.*;
import jakarta.validation.Valid;
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
        boolean sent = service.register(req.email, req.password);
        String message = sent
                ? "Registration successful. Please check your email to verify your account. It might takes some time."
                : "Registration successful. Email sending is disabled in this environment.";
        return new RegisterResponseDTO(message, sent);
    }

    @PostMapping("/login")
    public TokenDTO login(@Valid @RequestBody LoginDTO req) {
        String token = service.login(req.email, req.password);
        return new TokenDTO(token);
    }

    @GetMapping("/verify")
    public MessageDTO verify(@RequestParam String token) {
        service.verifyEmail(token);
        return new MessageDTO("Email verified successfully.");
    }

    @PostMapping("/resend-verification")
    public RegisterResponseDTO resendVerification(@Valid @RequestBody ResendVerificationDTO req) {
        boolean sent = service.resendVerification(req.email);
        String message = sent
                ? "Verification email sent."
                : "Email is already verified or email sending is disabled.";
        return new RegisterResponseDTO(message, sent);
    }

    @PostMapping("/forgot-password")
    public MessageDTO forgotPassword(@Valid @RequestBody ForgotPasswordDTO req) {
        service.forgotPassword(req.email);
        return new MessageDTO("If the account exists, a password reset email has been sent.");
    }

    @PostMapping("/reset-password")
    public MessageDTO resetPassword(@Valid @RequestBody ResetPasswordDTO req) {
        service.resetPassword(req.token, req.password);
        return new MessageDTO("Password reset successful. Please login with your new password.");
    }
}
