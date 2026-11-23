package com.creditscoring.auth.service;

import com.creditscoring.auth.entity.UserEntity;
import com.creditscoring.auth.repository.RoleRepository;
import com.creditscoring.auth.repository.UserRepository;
import com.creditscoring.common.error.AppException;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class UserAdminService {
    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final SessionStore sessions;
    private final AuthService auth;

    public UserAdminService(UserRepository users, RoleRepository roles, SessionStore sessions, AuthService auth) {
        this.users = users;
        this.roles = roles;
        this.sessions = sessions;
        this.auth = auth;
    }

    public record UserPage(List<UserView> items, long total, int page, int pageSize) {
    }

    @Transactional(readOnly = true)
    public UserPage search(String search, String role, String status, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw AppException.badRequest("page должен быть >= 1, pageSize - от 1 до 100");
        }
        Specification<UserEntity> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("email")), like), cb.like(cb.lower(root.get("fullName")), like)));
            }
            if (role != null && !role.isBlank()) {
                p.add(cb.equal(root.get("role").get("code"), role));
            }
            if (status != null && !status.isBlank()) {
                p.add(cb.equal(root.get("status"), status));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<UserEntity> res = users.findAll(spec, PageRequest.of(page - 1, pageSize, Sort.by("fullName")));
        return new UserPage(res.getContent().stream().map(auth::view).toList(), res.getTotalElements(), page, pageSize);
    }

    /** Пакетное получение пользователей (для связанных полей GraphQL: аналитик, автор и т. п.). */
    @Transactional(readOnly = true)
    public List<UserView> batch(List<UUID> ids) {
        return users.findByIdIn(ids).stream().map(auth::view).toList();
    }

    @Transactional
    public UserView setStatus(UUID id, boolean blocked, UUID actorId) {
        if (blocked && id.equals(actorId)) {
            throw AppException.conflict("Нельзя заблокировать самого себя");
        }
        UserEntity u = find(id);
        u.setStatus(blocked ? "BLOCKED" : "ACTIVE");
        if (blocked) {
            sessions.deleteAllForUser(id.toString());
        }
        log.info("ADMIN пользователь {} -> {}", u.getEmail(), u.getStatus());
        return auth.view(u);
    }

    @Transactional
    public UserView changeRole(UUID id, String roleCode, UUID actorId) {
        if (id.equals(actorId)) {
            throw AppException.conflict("Нельзя изменить собственную роль");
        }
        UserEntity u = find(id);
        u.setRole(roles.findById(roleCode).orElseThrow(() -> AppException.badRequest("Неизвестная роль " + roleCode)));
        if (!"BORROWER".equals(roleCode)) {
            u.setCompanyId(null);
        }
        sessions.deleteAllForUser(id.toString()); // роль изменилась - требуем повторный вход
        log.info("ADMIN пользователь {} -> роль {}", u.getEmail(), roleCode);
        return auth.view(u);
    }

    @Transactional
    public UserView assignCompany(UUID id, UUID companyId) {
        UserEntity u = find(id);
        if (!"BORROWER".equals(u.getRole().getCode())) {
            throw AppException.conflict("Компанию можно привязать только к заёмщику");
        }
        u.setCompanyId(companyId);
        log.info("ADMIN пользователь {} привязан к компании {}", u.getEmail(), companyId);
        return auth.view(u);
    }

    @Transactional(readOnly = true)
    public List<RoleView> roles() {
        return roles.findAll().stream()
                .map(r -> new RoleView(r.getCode(), r.getName(), r.getPermissions().stream().map(p -> p.getCode()).sorted().toList()))
                .toList();
    }

    public record RoleView(String code, String name, List<String> permissions) {
    }

    private UserEntity find(UUID id) {
        return users.findById(id).orElseThrow(() -> AppException.notFound("Пользователь"));
    }
}
