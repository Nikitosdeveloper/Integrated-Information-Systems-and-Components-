package com.creditscoring.common.grpc;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.error.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/** Преобразование ошибок между AppException и gRPC Status. */
public final class GrpcErrors {

    public static StatusRuntimeException toStatus(Throwable t) {
        if (t instanceof StatusRuntimeException s) {
            return s;
        }
        if (t instanceof AppException e) {
            Status status = switch (e.code()) {
                case UNAUTHENTICATED -> Status.UNAUTHENTICATED;
                case FORBIDDEN -> Status.PERMISSION_DENIED;
                case NOT_FOUND -> Status.NOT_FOUND;
                case BAD_REQUEST -> Status.INVALID_ARGUMENT;
                case CONFLICT -> Status.FAILED_PRECONDITION;
                case SERVICE_UNAVAILABLE -> Status.UNAVAILABLE;
                case TIMEOUT -> Status.DEADLINE_EXCEEDED;
                case INTERNAL -> Status.INTERNAL;
            };
            return status.withDescription(e.getMessage()).asRuntimeException();
        }
        // детали неожиданных ошибок наружу не отдаём
        return Status.INTERNAL.withDescription("Внутренняя ошибка сервиса").asRuntimeException();
    }

    public static AppException toApp(StatusRuntimeException e, String target) {
        String msg = e.getStatus().getDescription();
        return switch (e.getStatus().getCode()) {
            case UNAUTHENTICATED -> new AppException(ErrorCode.UNAUTHENTICATED, msg == null ? "Не аутентифицирован" : msg);
            case PERMISSION_DENIED -> new AppException(ErrorCode.FORBIDDEN, msg == null ? "Доступ запрещён" : msg);
            case NOT_FOUND -> new AppException(ErrorCode.NOT_FOUND, msg == null ? "Объект не найден" : msg);
            case INVALID_ARGUMENT -> new AppException(ErrorCode.BAD_REQUEST, msg == null ? "Некорректный запрос" : msg);
            case FAILED_PRECONDITION -> new AppException(ErrorCode.CONFLICT, msg == null ? "Операция недопустима" : msg);
            case DEADLINE_EXCEEDED -> new AppException(ErrorCode.TIMEOUT, "Сервис " + target + " не ответил вовремя");
            case UNAVAILABLE -> new AppException(ErrorCode.SERVICE_UNAVAILABLE, "Сервис " + target + " временно недоступен");
            default -> new AppException(ErrorCode.INTERNAL, "Ошибка сервиса " + target);
        };
    }

    private GrpcErrors() {
    }
}
