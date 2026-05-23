package com.upb.dfs.namenode.service;

import com.upb.dfs.common.dto.AuthDtos;
import com.upb.dfs.common.util.JwtUtil;
import com.upb.dfs.namenode.entity.UserEntity;
import com.upb.dfs.namenode.repo.UserRepo;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepo userRepo;
    private final BCryptPasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public UserService(UserRepo userRepo, BCryptPasswordEncoder encoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    public UserEntity register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.length() < 4) {
            throw new IllegalArgumentException("Invalid username/password");
        }
        if (userRepo.findByUsername(username).isPresent()) {
            throw new IllegalStateException("User already exists");
        }
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        return userRepo.save(u);
    }

    public AuthDtos.LoginResponse login(String username, String password) {
        UserEntity u = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!encoder.matches(password, u.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        AuthDtos.LoginResponse resp = new AuthDtos.LoginResponse();
        resp.token = jwtUtil.generate(u.getUsername(), u.getId());
        resp.expiresAt = System.currentTimeMillis() + jwtUtil.getExpirationMillis();
        resp.username = u.getUsername();
        return resp;
    }
}
