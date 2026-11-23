package com.creditscoring.common.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.AbstractStub;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Создаёт устойчивые gRPC-клиенты с общими настройками timeout/retry (grpc.client.*). */
@Component
public class GrpcClientFactory implements DisposableBean {
    private final long timeoutMs;
    private final int maxAttempts;
    private final long backoffMs;
    private final List<ManagedChannel> channels = new ArrayList<>();

    public GrpcClientFactory(@Value("${grpc.client.timeout-ms:3000}") long timeoutMs,
                             @Value("${grpc.client.max-attempts:3}") int maxAttempts,
                             @Value("${grpc.client.backoff-ms:150}") long backoffMs) {
        this.timeoutMs = timeoutMs;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    public <S extends AbstractStub<S>> ServiceCaller<S> create(String name, String host, int port,
                                                              Function<io.grpc.Channel, S> stubFactory) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        channels.add(channel);
        return new ServiceCaller<>(name, stubFactory.apply(channel), timeoutMs, maxAttempts, backoffMs);
    }

    @Override
    public void destroy() throws Exception {
        for (ManagedChannel c : channels) {
            c.shutdown().awaitTermination(2, TimeUnit.SECONDS);
        }
    }
}
