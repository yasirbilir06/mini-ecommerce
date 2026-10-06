package com.yasir.miniecommerce.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {
    @GetMapping("/api/admin/test")
    public String adminTest() {
        return "Admin erişimi başarılı!";
    }

}