package com.creditscoring.gateway.graphql;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.security.Actor;
import com.creditscoring.gateway.auth.AuthClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Перед выполнением любого запроса: читает Authorization: Bearer, проверяет JWT и активную сессию через Auth Service
 * и кладёт пользователя в контекст. Запрос без валидного токена выполняется только для публичных операций:
 * остальные resolvers требуют пользователя и вернут UNAUTHENTICATED.
 */
@Component
public class ActorInterceptor implements WebGraphQlInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ActorInterceptor.class);

    private final AuthClient auth;

    public ActorInterceptor(AuthClient auth) {
        this.auth = auth;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() > 7) {
            String token = header.substring(7).trim();
            try {
                Map<String, Object> user = auth.validate(token);
                Actor actor = new Actor((String) user.get("id"), (String) user.get("email"), (String) user.get("role"),
                        user.get("companyId") == null ? "" : (String) user.get("companyId"));
                request.configureExecutionInput((input, builder) -> builder.graphQLContext(c -> {
                    c.put("actor", actor);
                    c.put("user", user);
                    c.put("token", token);
                }).build());
                log.info("AUTH запрос от {}", actor);
            } catch (AppException e) {
                log.warn("AUTH токен отклонён: {} ({})", e.getMessage(), e.code());
                request.configureExecutionInput((input, builder) -> builder.graphQLContext(c -> c.put("authError", e)).build());
            }
        }
        return chain.next(request);
    }
}
