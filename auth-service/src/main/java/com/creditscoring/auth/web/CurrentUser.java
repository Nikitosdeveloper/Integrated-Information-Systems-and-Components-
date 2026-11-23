package com.creditscoring.auth.web;

import com.creditscoring.auth.service.AuthService;
import com.creditscoring.auth.service.UserView;
import com.creditscoring.common.error.AppException;
import com.creditscoring.common.error.ErrorCode;
import org.springframework.stereotype.Component;

/** Определение пользователя по заголовку Authorization: Bearer (JWT + активная сессия). */
@Component
public class CurrentUser {
    private final AuthService auth;

    public CurrentUser(AuthService auth) {
        this.auth = auth;
    }

    public static String bearer(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() < 8) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Требуется заголовок Authorization: Bearer <token>");
        }
        return header.substring(7).trim();
    }

    public UserView require(String header) {
        return auth.validate(bearer(header));
    }

    public UserView requireAdmin(String header) {
        UserView u = require(header);
        if (!"ADMIN".equals(u.role())) {
            throw AppException.forbidden("Операция доступна только администратору");
        }
        return u;
    }
}
