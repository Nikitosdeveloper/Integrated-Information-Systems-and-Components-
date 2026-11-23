package com.creditscoring.auth.web;

import com.creditscoring.auth.service.AuthService;
import com.creditscoring.auth.service.UserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72, message = "пароль должен содержать от 8 до 72 символов") String password,
                                  @NotBlank @Size(max = 200) String fullName) {
    }

    @PostMapping("/register")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(r.email(), r.password(), r.fullName()));
    }

    @PostMapping("/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r.email(), r.password());
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(CurrentUser.bearer(authorization));
        return Map.of("ok", true);
    }

    /** Вызывается шлюзом на каждый запрос: проверка JWT и активной сессии, возврат пользователя и его прав. */
    @GetMapping("/validate")
    public UserView validate(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return auth.validate(CurrentUser.bearer(authorization));
    }
}
