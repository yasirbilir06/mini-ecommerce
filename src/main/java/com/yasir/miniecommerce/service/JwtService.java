package com.yasir.miniecommerce.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expirationTime;
    public String generateToken(String email) {

        SecretKey key = Keys.hmacShaKeyFor(
                secretKey.getBytes(StandardCharsets.UTF_8)
        );

        return Jwts.builder()
                //JWT'nin içine kullanıcının email'ini koyuyor.
                .subject(email)
                .issuedAt(new Date())
                //token'ın 1 saat geçerli olmasını sağlıyor.
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                //JWT'yi gizli anahtarla imzalıyor.
                .signWith(key)
                //sonunda bize gerçek JWT stringini veriyor.
                .compact();
    }
    public String extractEmail(String token) {

        SecretKey key = Keys.hmacShaKeyFor(
                secretKey.getBytes(StandardCharsets.UTF_8)
        );

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}