package com.example.fileshare.dto;

public class RequestDTO {
    public static class RegisterDTO {
        public String email;
        public String password;
    }

    public static class LoginDTO {
        public String email;
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
