package com.example.fileshare.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RequestDTO {
    public static class RegisterDTO {
        @NotBlank
        @Email(message = "Email must be valid")
        public String email;
        @NotBlank
        @Size(min = 6, message = "Password must be at least 6 characters")
        public String password;
    }

    public static class LoginDTO {
        @NotBlank
        @Email(message = "Email must be valid")
        public String email;
        @NotBlank
        public String password;
    }

    public static class ResendVerificationDTO {
        @NotBlank
        @Email(message = "Email must be valid")
        public String email;
    }

    public static class ForgotPasswordDTO {
        @NotBlank
        @Email(message = "Email must be valid")
        public String email;
    }

    public static class ResetPasswordDTO {
        @NotBlank
        public String token;
        @NotBlank
        @Size(min = 6, message = "Password must be at least 6 characters")
        public String password;
    }

    public static class TokenDTO {
        public String token;
        public TokenDTO(String token) { this.token = token; }
    }

    public static class RegisterResponseDTO {
        public String message;
        public boolean verificationEmailSent;

        public RegisterResponseDTO(String message, boolean verificationEmailSent) {
            this.message = message;
            this.verificationEmailSent = verificationEmailSent;
        }
    }

    public static class MessageDTO {
        public String message;

        public MessageDTO(String message) {
            this.message = message;
        }
    }
}
