package com.upb.dfs.common.dto;

public class AuthDtos {
    public static class LoginRequest {
        public String username;
        public String password;
    }

    public static class RegisterRequest {
        public String username;
        public String password;
    }

    public static class LoginResponse {
        public String token;
        public long expiresAt;
        public String username;
    }
}
