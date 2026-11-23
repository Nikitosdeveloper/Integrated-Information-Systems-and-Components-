package com.creditscoring.gateway.auth;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * REST-клиент Auth Service. Шлюз не хранит пользователей и не проверяет пароли: всё делегируется Auth Service.
 * Вызовы ограничены timeout; идемпотентные запросы (GET) повторяются ограниченное число раз при сбоях связи.
 */
@Component
public class AuthClient {
    private static final Logger log = LoggerFactory.getLogger("auth.client");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() { };

    private final RestClient rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxAttempts;

    public AuthClient(@Value("${auth-service.url}") String url,
                      @Value("${auth-service.connect-timeout-ms}") int connectTimeout,
                      @Value("${auth-service.read-timeout-ms}") int readTimeout,
                      @Value("${auth-service.max-attempts}") int maxAttempts) {
        // JDK HttpClient (а не HttpURLConnection): тело ответа 401 доступно для разбора
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeout)).build());
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        this.rest = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.maxAttempts = maxAttempts;
    }

    public Map<String, Object> login(String email, String password) {
        return call("login", false, () -> rest.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password)).retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    public Map<String, Object> register(String email, String password, String fullName) {
        return call("register", false, () -> rest.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password, "fullName", fullName)).retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    public void logout(String token) {
        call("logout", false, () -> rest.post().uri("/auth/logout").header("Authorization", "Bearer " + token)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    /** Проверка JWT и наличия активной сессии в Redis: выполняется на каждый запрос. */
    public Map<String, Object> validate(String token) {
        return call("validate", true, () -> rest.get().uri("/auth/validate").header("Authorization", "Bearer " + token)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    public Map<String, Object> users(String token, String search, String role, String status, int page, int pageSize) {
        return call("users", true, () -> rest.get().uri(b -> {
            b.path("/users").queryParam("page", page).queryParam("pageSize", pageSize);
            if (search != null) b.queryParam("search", search);
            if (role != null) b.queryParam("role", role);
            if (status != null) b.queryParam("status", status);
            return b.build();
        }).header("Authorization", "Bearer " + token).retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    public List<Map<String, Object>> batch(String token, List<String> ids) {
        return call("users.batch", true, () -> rest.get().uri(b -> b.path("/users/batch").queryParam("ids", String.join(",", ids)).build())
                .header("Authorization", "Bearer " + token).retrieve().onStatus(s -> s.isError(), this::fail).body(LIST));
    }

    public List<Map<String, Object>> roles(String token) {
        return call("roles", true, () -> rest.get().uri("/roles").header("Authorization", "Bearer " + token)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(LIST));
    }

    public Map<String, Object> put(String token, String path, Map<String, String> body) {
        return call("PUT " + path, false, () -> rest.put().uri(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    private <T> T call(String op, boolean idempotent, Supplier<T> action) {
        int attempts = idempotent ? maxAttempts : 1;
        for (int attempt = 1; ; attempt++) {
            long started = System.nanoTime();
            try {
                T result = action.get();
                log.info("AUTH CALL {} attempt={} OK {} ms", op, attempt, (System.nanoTime() - started) / 1_000_000);
                return result;
            } catch (ResourceAccessException e) { // сеть: недоступен или не ответил за timeout
                if (attempt >= attempts) {
                    log.error("AUTH CALL {} attempt={} недоступен: {}", op, attempt, e.getMessage());
                    throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "Сервис аутентификации временно недоступен");
                }
                log.warn("AUTH CALL {} attempt={} сбой связи, повтор: {}", op, attempt, e.getMessage());
            } catch (AppException e) {
                log.warn("AUTH CALL {} attempt={} {}: {}", op, attempt, e.code(), e.getMessage());
                throw e;
            }
        }
    }

    private void fail(org.springframework.http.HttpRequest req, org.springframework.http.client.ClientHttpResponse res) throws java.io.IOException {
        ErrorCode code = switch (res.getStatusCode().value()) {
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 400 -> ErrorCode.BAD_REQUEST;
            case 409 -> ErrorCode.CONFLICT;
            case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> ErrorCode.INTERNAL;
        };
        String message = "Ошибка сервиса аутентификации";
        try {
            JsonNode node = mapper.readTree(res.getBody());
            if (node.hasNonNull("message")) {
                message = node.get("message").asText();
            }
        } catch (Exception ignored) {
            // тело не JSON - оставляем общее сообщение
        }
        throw new AppException(code, message);
    }
}
