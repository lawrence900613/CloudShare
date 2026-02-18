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
}
