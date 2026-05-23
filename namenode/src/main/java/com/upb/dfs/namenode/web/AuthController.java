package com.upb.dfs.namenode.web;

import com.upb.dfs.common.dto.AuthDtos;
import com.upb.dfs.namenode.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthDtos.RegisterRequest req) {
        userService.register(req.username, req.password);
        return ResponseEntity.ok(Map.of("status", "ok", "username", req.username));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(@RequestBody AuthDtos.LoginRequest req) {
        return ResponseEntity.ok(userService.login(req.username, req.password));
    }
}
