package com.creditscoring.common.grpc;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.error.ErrorCode;
import com.creditscoring.common.security.Actor;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * gRPC-клиент одного сервиса с механизмами устойчивости:
 * <ul>
 *   <li>timeout - каждая попытка ограничена дедлайном;</li>
 *   <li>retry - только для временных ошибок (UNAVAILABLE; DEADLINE_EXCEEDED - для идемпотентных чтений),
 *       число попыток ограничено, между попытками растущая пауза;</li>
 *   <li>логирование каждой попытки, результата и длительности;</li>
 *   <li>преобразование итоговой ошибки в AppException.</li>
 * </ul>
 */
public class ServiceCaller<S extends AbstractStub<S>> {
    private static final Logger log = LoggerFactory.getLogger("grpc.client");

    private final String target;
    private final S stub;
    private final long timeoutMs;
    private final int maxAttempts;
    private final long backoffMs;

    public ServiceCaller(String target, S stub, long timeoutMs, int maxAttempts, long backoffMs) {
        this.target = target;
        this.stub = stub;
        this.timeoutMs = timeoutMs;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = backoffMs;
    }

    /**
     * @param idempotent true для операций чтения: их безопасно повторять и после таймаута.
     *                   Изменяющие операции повторяются только при UNAVAILABLE (запрос заведомо не дошёл).
     */
    public <T> T call(String op, Actor actor, boolean idempotent, Function<S, T> fn) {
        Metadata md = headers(actor);
        S base = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md));
        StatusRuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long started = System.nanoTime();
            try {
                T result = fn.apply(base.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS));
                log.info("gRPC CALL {}.{} attempt={} OK {} ms", target, op, attempt, elapsed(started));
                return result;
            } catch (StatusRuntimeException e) {
                last = e;
                Status.Code code = e.getStatus().getCode();
                boolean transientError = code == Status.Code.UNAVAILABLE
                        || (idempotent && code == Status.Code.DEADLINE_EXCEEDED);
                if (!transientError) {
                    // бизнес-ошибка или ошибка клиента: повторять бессмысленно
                    log.warn("gRPC CALL {}.{} attempt={} {} ({}) {} ms", target, op, attempt, code, e.getStatus().getDescription(), elapsed(started));
                    throw GrpcErrors.toApp(e, target);
                }
                if (attempt < maxAttempts) {
                    long pause = backoffMs * (1L << (attempt - 1));
                    log.warn("gRPC CALL {}.{} attempt={} {} {} ms - повтор через {} ms", target, op, attempt, code, elapsed(started), pause);
                    sleep(pause);
                } else {
                    log.error("gRPC CALL {}.{} attempt={} {} - попытки исчерпаны", target, op, attempt, code);
                }
            }
        }
        throw GrpcErrors.toApp(last, target);
    }

    private Metadata headers(Actor actor) {
        Metadata md = new Metadata();
        Actor a = actor == null ? Actor.SYSTEM : actor;
        md.put(Meta.USER_ID, a.userId());
        md.put(Meta.USER_EMAIL, a.email() == null ? "" : a.email());
        md.put(Meta.USER_ROLE, a.role());
        md.put(Meta.COMPANY_ID, a.companyId() == null ? "" : a.companyId());
        String cid = MDC.get(Meta.MDC_CID);
        if (cid == null) {
            cid = Meta.CID_CTX.get();
        }
        if (cid != null) {
            md.put(Meta.CORRELATION_ID, cid);
        }
        return md;
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.INTERNAL, "Ожидание повтора прервано");
        }
    }
}
