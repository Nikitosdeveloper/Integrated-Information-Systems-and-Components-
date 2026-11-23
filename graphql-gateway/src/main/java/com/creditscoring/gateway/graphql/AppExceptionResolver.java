package com.creditscoring.gateway.graphql;

import com.creditscoring.common.error.AppException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Единообразное преобразование ошибок сервисов в GraphQL-ошибки с кодом в extensions.code. Внутренние детали клиенту не раскрываются. */
@Component
public class AppExceptionResolver extends DataFetcherExceptionResolverAdapter {
    private static final Logger log = LoggerFactory.getLogger(AppExceptionResolver.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof AppException app) {
            ErrorType type = switch (app.code()) {
                case NOT_FOUND -> ErrorType.NOT_FOUND;
                case FORBIDDEN -> ErrorType.FORBIDDEN;
                case UNAUTHENTICATED -> ErrorType.UNAUTHORIZED;
                case BAD_REQUEST, CONFLICT -> ErrorType.BAD_REQUEST;
                case SERVICE_UNAVAILABLE, TIMEOUT, INTERNAL -> ErrorType.INTERNAL_ERROR;
            };
            if (type == ErrorType.INTERNAL_ERROR) {
                log.error("ERROR {} в {}: {}", app.code(), env.getField().getName(), app.getMessage());
            }
            return GraphqlErrorBuilder.newError(env).errorType(type).message(app.getMessage())
                    .extensions(Map.of("code", app.code().name())).build();
        }
        log.error("Необработанная ошибка в {}", env.getField().getName(), ex);
        return GraphqlErrorBuilder.newError(env).errorType(ErrorType.INTERNAL_ERROR).message("Внутренняя ошибка сервера")
                .extensions(Map.of("code", "INTERNAL")).build();
    }
}
