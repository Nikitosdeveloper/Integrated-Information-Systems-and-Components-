package com.creditscoring.common.grpc;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.security.Actor;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/** Вспомогательные методы для реализации gRPC-обработчиков: обработчик остаётся тонким, логика - в сервисном слое. */
public final class Grpc {
    private static final Logger log = LoggerFactory.getLogger(Grpc.class);

    public static <T> void reply(StreamObserver<T> observer, Supplier<T> action) {
        try {
            observer.onNext(action.get());
            observer.onCompleted();
        } catch (AppException e) {
            observer.onError(GrpcErrors.toStatus(e));
        } catch (Throwable t) {
            log.error("Необработанная ошибка в gRPC-обработчике", t);
            observer.onError(GrpcErrors.toStatus(t));
        }
    }

    /** Пользователь текущего вызова (из метаданных, проставленных шлюзом). Без него вызов запрещён. */
    public static Actor actor() {
        Actor actor = Meta.ACTOR_CTX.get();
        if (actor == null) {
            throw new AppException(com.creditscoring.common.error.ErrorCode.UNAUTHENTICATED, "Не передан контекст пользователя");
        }
        return actor;
    }

    private Grpc() {
    }
}
