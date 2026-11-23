package com.creditscoring.common.security;

import com.creditscoring.common.error.AppException;

import java.util.Arrays;
import java.util.UUID;

/**
 * Проверенная информация о пользователе. Определяется шлюзом после проверки JWT и сессии
 * и передаётся во внутренние сервисы в gRPC-метаданных. Внутренние сервисы JWT не проверяют.
 */
public record Actor(String userId, String email, String role, String companyId) {

    public static final Actor SYSTEM = new Actor("00000000-0000-0000-0000-000000000000", "system@internal", Roles.SYSTEM, "");

    public boolean hasRole(String... roles) {
        return Arrays.asList(roles).contains(role);
    }

    /** Бросает FORBIDDEN, если роль пользователя не входит в список. */
    public Actor requireRole(String... roles) {
        if (!hasRole(roles)) {
            throw AppException.forbidden("Недостаточно прав: операция недоступна для роли " + role);
        }
        return this;
    }

    public boolean isBorrower() {
        return Roles.BORROWER.equals(role);
    }

    public UUID userUuid() {
        return UUID.fromString(userId);
    }

    public UUID companyUuid() {
        if (companyId == null || companyId.isBlank()) {
            throw AppException.forbidden("Пользователь не связан с компанией");
        }
        return UUID.fromString(companyId);
    }

    @Override
    public String toString() {
        return role + ":" + email;
    }
}
