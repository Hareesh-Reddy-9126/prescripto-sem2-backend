package com.prescripto.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private Key signingKey() {
        String normalizedSecret = jwtSecret;
        if (normalizedSecret == null || normalizedSecret.length() < 32) {
            normalizedSecret = (normalizedSecret == null ? "" : normalizedSecret) + "01234567890123456789012345678901";
            normalizedSecret = normalizedSecret.substring(0, 32);
        }
        return Keys.hmacShaKeyFor(normalizedSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateIdToken(String id) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        return Jwts.builder()
            .claims(claims)
            .issuedAt(new Date())
            .signWith(signingKey())
            .compact();
    }

    public String generateRawToken(String value) {
        return Jwts.builder()
            .subject(value)
            .issuedAt(new Date())
            .signWith(signingKey())
            .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(signingKey().getEncoded()))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String extractId(String token) {
        Claims claims = parseClaims(token);
        Object id = claims.get("id");
        return id == null ? null : String.valueOf(id);
    }

    public String extractRaw(String token) {
        Claims claims = parseClaims(token);
        return claims.getSubject();
    }
}
