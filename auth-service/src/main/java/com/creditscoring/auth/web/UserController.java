package com.creditscoring.auth.web;

import com.creditscoring.auth.service.UserAdminService;
import com.creditscoring.auth.service.UserView;
import com.creditscoring.common.error.AppException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class UserController {
    private final UserAdminService admin;
    private final CurrentUser current;

    public UserController(UserAdminService admin, CurrentUser current) {
        this.admin = admin;
        this.current = current;
    }

    @GetMapping("/users")
    public UserAdminService.UserPage list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(required = false) String role,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int pageSize) {
        current.requireAdmin(authorization);
        return admin.search(search, role, status, page, pageSize);
    }

    @GetMapping("/users/batch")
    public List<UserView> batch(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @RequestParam String ids) {
        current.require(authorization);
        try {
            return admin.batch(Arrays.stream(ids.split(",")).filter(s -> !s.isBlank()).map(String::trim).map(UUID::fromString).toList());
        } catch (IllegalArgumentException e) {
            throw AppException.badRequest("Некорректный идентификатор пользователя");
        }
    }

    @PutMapping("/users/{id}/block")
    public UserView block(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id) {
        UserView me = current.requireAdmin(authorization);
        return admin.setStatus(id, true, UUID.fromString(me.id()));
    }

    @PutMapping("/users/{id}/unblock")
    public UserView unblock(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id) {
        UserView me = current.requireAdmin(authorization);
        return admin.setStatus(id, false, UUID.fromString(me.id()));
    }

    @PutMapping("/users/{id}/role")
    public UserView role(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id,
                         @RequestBody Map<String, String> body) {
        UserView me = current.requireAdmin(authorization);
        return admin.changeRole(id, body.get("role"), UUID.fromString(me.id()));
    }

    @PutMapping("/users/{id}/company")
    public UserView company(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id,
                            @RequestBody Map<String, String> body) {
        current.requireAdmin(authorization);
        return admin.assignCompany(id, UUID.fromString(body.get("companyId")));
    }

    @GetMapping("/roles")
    public List<UserAdminService.RoleView> roles(@RequestHeader(value = "Authorization", required = false) String authorization) {
        current.requireAdmin(authorization);
        return admin.roles();
    }
}
