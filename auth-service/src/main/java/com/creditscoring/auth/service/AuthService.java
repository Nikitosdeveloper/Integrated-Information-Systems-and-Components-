package com.creditscoring.auth.service;

import com.creditscoring.auth.entity.RoleEntity;
import com.creditscoring.auth.entity.UserEntity;
import com.creditscoring.auth.repository.RoleRepository;
import com.creditscoring.auth.repository.UserRepository;
import com.creditscoring.common.error.AppException;
import com.creditscoring.common.error.ErrorCode;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILURES = 5;

    private final UserRepository users;
    private final RoleRepository roles;
    private final TokenService tokens;
    private final SessionStore sessions;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final long sessionTtlMinutes;

    public AuthService(UserRepository users, RoleRepository roles, TokenService tokens, SessionStore sessions,
                       @Value("${auth.session-ttl-minutes}") long sessionTtlMinutes) {
        this.users = users;
        this.roles = roles;
        this.tokens = tokens;
        this.sessions = sessions;
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    public record LoginResult(String token, long expiresInSeconds, UserView user) {
    }

    /** Регистрация: всегда роль BORROWER, без привязки к компании (привязку выполняет администратор). */
    @Transactional
    public UserView register(String email, String password, String fullName) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw AppException.conflict("Пользователь с таким email уже зарегистрирован");
        }
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setFullName(fullName.trim());
        u.setRole(roles.getReferenceById("BORROWER"));
        u.setStatus("ACTIVE");
        u.setCreatedAt(OffsetDateTime.now());
        users.save(u);
        log.info("AUTH регистрация user={} role=BORROWER", u.getEmail());
        return view(users.findById(u.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password) {
        if (sessions.failures(email) >= MAX_FAILURES) {
            log.warn("AUTH вход заблокирован (слишком много попыток) user={}", email);
            throw AppException.forbidden("Слишком много неудачных попыток входа, повторите позже");
        }
        UserEntity u = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        // одинаковое сообщение для неизвестного email и неверного пароля
        if (u == null || !encoder.matches(password, u.getPasswordHash())) {
            sessions.incrementFailures(email, Duration.ofMinutes(10));
            log.warn("AUTH неудачный вход user={}", email);
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Неверный email или пароль");
        }
        if (!u.isActive()) {
            log.warn("AUTH вход заблокированного пользователя user={}", email);
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Пользователь заблокирован");
        }
        sessions.clearFailures(email);
        String sessionId = UUID.randomUUID().toString();
        sessions.create(sessionId, u.getId().toString(), u.getRole().getCode());
        String token = tokens.issue(u.getId().toString(), sessionId, u.getRole().getCode(), u.getEmail());
        log.info("AUTH вход выполнен user={} role={}", u.getEmail(), u.getRole().getCode());
        return new LoginResult(token, sessionTtlMinutes * 60, view(u));
    }

    /** Проверка токена И наличия активной сессии. Без сессии в Redis валидный JWT не принимается. */
    @Transactional(readOnly = true)
    public UserView validate(String token) {
        Claims claims = tokens.parse(token);
        String sessionId = claims.getId();
        String userId = claims.getSubject();
        if (sessionId == null || !sessions.touch(sessionId)) {
            log.warn("AUTH сессия отсутствует или истекла sid={} user={}", sessionId, userId);
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Сессия завершена или истекла");
        }
        UserEntity u = users.findById(UUID.fromString(userId)).orElse(null);
        if (u == null || !u.isActive()) {
            sessions.delete(sessionId, userId);
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Пользователь недоступен");
        }
        return view(u);
    }

    public void logout(String token) {
        Claims claims = tokens.parse(token);
        sessions.delete(claims.getId(), claims.getSubject());
    }

    UserView view(UserEntity u) {
        RoleEntity role = roles.findById(u.getRole().getCode()).orElseThrow();
        return new UserView(u.getId().toString(), u.getEmail(), u.getFullName(), role.getCode(),
                u.getCompanyId() == null ? null : u.getCompanyId().toString(), u.getStatus(),
                role.getPermissions().stream().map(p -> p.getCode()).sorted().toList());
    }
}
