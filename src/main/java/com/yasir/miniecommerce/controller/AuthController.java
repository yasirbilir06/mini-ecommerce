package com.yasir.miniecommerce.controller;

import com.yasir.miniecommerce.dto.LoginRequest;
import com.yasir.miniecommerce.dto.LoginResponse;
import com.yasir.miniecommerce.dto.RegisterRequest;
import com.yasir.miniecommerce.dto.RegisterResponse;
import com.yasir.miniecommerce.model.User;
import com.yasir.miniecommerce.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}