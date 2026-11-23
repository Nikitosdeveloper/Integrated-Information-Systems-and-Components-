package com.creditscoring.gateway.graphql;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.error.ErrorCode;
import com.creditscoring.common.security.Actor;
import graphql.GraphQLContext;

/** Доступ к проверенному пользователю в контексте запроса. Данные о пользователе берутся только из Auth Service. */
public final class Ctx {

    public static Actor actor(GraphQLContext ctx) {
        Actor actor = ctx.get("actor");
        if (actor != null) {
            return actor;
        }
        AppException authError = ctx.get("authError");
        if (authError != null) {
            throw authError;
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED, "Требуется аутентификация: передайте заголовок Authorization: Bearer <token>");
    }

    public static Actor actor(GraphQLContext ctx, String... roles) {
        return actor(ctx).requireRole(roles);
    }

    public static String token(GraphQLContext ctx) {
        String token = ctx.get("token");
        if (token == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Требуется аутентификация");
        }
        return token;
    }

    private Ctx() {
    }
}
