package com.yasir.miniecommerce.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @Email(message = "Geçerli bir email adresi giriniz")
    @NotBlank(message = "Email boş bırakılamaz")
    private String email;
    @NotBlank(message = "Şifre boş bırakılamaz")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}