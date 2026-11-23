package com.creditscoring.common.grpc;

import com.creditscoring.common.security.Actor;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Серверный перехватчик: извлекает пользователя и correlation id из метаданных,
 * кладёт их в контекст вызова и MDC, логирует вход, результат и длительность каждого вызова.
 */
public class ServerContextInterceptor implements ServerInterceptor {
    private static final Logger log = LoggerFactory.getLogger("grpc.server");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String cid = headers.get(Meta.CORRELATION_ID);
        if (cid == null || cid.isBlank()) {
            cid = UUID.randomUUID().toString().substring(0, 8);
        }
        Actor actor = null;
        String userId = headers.get(Meta.USER_ID);
        String role = headers.get(Meta.USER_ROLE);
        if (userId != null && role != null) {
            String company = headers.get(Meta.COMPANY_ID);
            actor = new Actor(userId, headers.get(Meta.USER_EMAIL), role, company == null ? "" : company);
        }
        final String correlationId = cid;
        final Actor callActor = actor;
        final String method = call.getMethodDescriptor().getFullMethodName();
        final long started = System.nanoTime();

        MDC.put(Meta.MDC_CID, correlationId);
        log.info("gRPC IN  {} actor={}", method, callActor == null ? "-" : callActor);

        ServerCall<ReqT, RespT> loggingCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                long ms = (System.nanoTime() - started) / 1_000_000;
                MDC.put(Meta.MDC_CID, correlationId);
                if (status.isOk()) {
                    log.info("gRPC OUT {} OK {} ms", method, ms);
                } else {
                    log.warn("gRPC OUT {} {} ({}) {} ms", method, status.getCode(), status.getDescription(), ms);
                }
                super.close(status, trailers);
            }
        };

        Context ctx = Context.current().withValue(Meta.ACTOR_CTX, callActor).withValue(Meta.CID_CTX, correlationId);
        ServerCall.Listener<ReqT> delegate = Contexts.interceptCall(ctx, loggingCall, headers, next);
        MDC.remove(Meta.MDC_CID);

        // MDC привязан к потоку, поэтому выставляем его на каждый обратный вызов
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                MDC.put(Meta.MDC_CID, correlationId);
                try {
                    super.onMessage(message);
                } finally {
                    MDC.remove(Meta.MDC_CID);
                }
            }

            @Override
            public void onHalfClose() {
                MDC.put(Meta.MDC_CID, correlationId);
                try {
                    super.onHalfClose();
                } finally {
                    MDC.remove(Meta.MDC_CID);
                }
            }
        };
    }
}
