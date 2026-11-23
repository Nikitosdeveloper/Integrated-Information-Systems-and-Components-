package com.creditscoring.common.error;

/** Коды ошибок системы. Передаются через gRPC и попадают в extensions.code GraphQL-ошибок. */
public enum ErrorCode {
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    BAD_REQUEST,
    /** Операция недопустима в текущем состоянии объекта (нарушено бизнес-правило). */
    CONFLICT,
    /** Сервис-получатель недоступен (после исчерпания повторов). */
    SERVICE_UNAVAILABLE,
    /** Превышено время ожидания ответа сервиса. */
    TIMEOUT,
    INTERNAL
}
