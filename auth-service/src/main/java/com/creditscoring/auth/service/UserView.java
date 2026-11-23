package com.creditscoring.auth.service;

import java.util.List;

/** Представление пользователя для API (хэш пароля наружу не выдаётся). */
public record UserView(String id, String email, String fullName, String role, String companyId, String status,
                       List<String> permissions) {
}
