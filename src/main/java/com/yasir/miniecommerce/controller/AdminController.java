package com.yasir.miniecommerce.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {
    @GetMapping("/api/admin/test")
    @SecurityRequirement(name = "bearerAuth")
    public String adminTest() {
        return "Admin erişimi başarılı!";
    }

}
