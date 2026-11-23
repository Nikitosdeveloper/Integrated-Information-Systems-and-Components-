package com.creditscoring.auth.service;

import com.creditscoring.auth.entity.UserEntity;
import com.creditscoring.auth.repository.RoleRepository;
import com.creditscoring.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Тестовые пользователи (пароль у всех: Test1234!). Создаются, только если таблица пуста. */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    static final String TEST_PASSWORD = "Test1234!";

    private final UserRepository users;
    private final RoleRepository roles;
    private final boolean enabled;

    public DataSeeder(UserRepository users, RoleRepository roles, @Value("${auth.seed-users:true}") boolean enabled) {
        this.users = users;
        this.roles = roles;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || users.count() > 0) {
            return;
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(TEST_PASSWORD);
        add(1, "admin@bank.test", "Администратор Системы", "ADMIN", null, "ACTIVE", hash);
        add(2, "analyst1@bank.test", "Анна Аналитикова", "ANALYST", null, "ACTIVE", hash);
        add(3, "analyst2@bank.test", "Антон Расчётов", "ANALYST", null, "ACTIVE", hash);
        add(4, "committee@bank.test", "Кирилл Комитетов", "COMMITTEE", null, "ACTIVE", hash);
        add(5, "borrower1@alfa.test", "Борис Альфов", "BORROWER", "c0000000-0000-4000-8000-000000000001", "ACTIVE", hash);
        add(6, "borrower2@stroy.test", "Сергей Строев", "BORROWER", "c0000000-0000-4000-8000-000000000002", "ACTIVE", hash);
        add(7, "borrower3@soft.test", "Софья Софтова", "BORROWER", "c0000000-0000-4000-8000-000000000003", "ACTIVE", hash);
        add(8, "blocked.analyst@bank.test", "Заблокированный Аналитик", "ANALYST", null, "BLOCKED", hash);
        log.info("SEED создано 8 тестовых пользователей");
    }

    private void add(int n, String email, String name, String role, String company, String status, String hash) {
        UserEntity u = new UserEntity();
        u.setId(UUID.fromString("a0000000-0000-4000-8000-%012d".formatted(n)));
        u.setEmail(email);
        u.setFullName(name);
        u.setRole(roles.getReferenceById(role));
        u.setCompanyId(company == null ? null : UUID.fromString(company));
        u.setStatus(status);
        u.setPasswordHash(hash);
        u.setCreatedAt(OffsetDateTime.now());
        users.save(u);
    }
}
