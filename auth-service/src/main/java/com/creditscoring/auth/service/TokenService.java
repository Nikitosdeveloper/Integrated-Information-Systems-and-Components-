package com.creditscoring.auth.service;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/** Выпуск и проверка JWT (HS256). Сам по себе валидный JWT не даёт доступа: нужна активная сессия в Redis. */
@Component
public class TokenService {
    private final SecretKey key;
    private final Duration ttl;

    public TokenService(@Value("${auth.jwt.secret}") String secret,
                        @Value("${auth.session-ttl-minutes}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // сам токен живёт дольше сессии; фактическое завершение определяется сессией в Redis
        this.ttl = Duration.ofMinutes(ttlMinutes).multipliedBy(16);
    }

    public String issue(String userId, String sessionId, String role, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .id(sessionId)
                .claim("role", role)
                .claim("email", email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Недействительный или просроченный токен");
        }
    }
}
