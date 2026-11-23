package com.creditscoring.common.grpc;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Запускает gRPC-сервер сервиса вместе с Spring-контекстом и останавливает его при завершении. */
public class GrpcServerRunner implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(GrpcServerRunner.class);

    private final int port;
    private final List<BindableService> services;
    private Server server;

    public GrpcServerRunner(int port, List<BindableService> services) {
        this.port = port;
        this.services = services;
    }

    @Override
    public void start() {
        ServerBuilder<?> builder = ServerBuilder.forPort(port).intercept(new ServerContextInterceptor());
        services.forEach(builder::addService);
        server = builder.build();
        try {
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось запустить gRPC-сервер на порту " + port, e);
        }
        log.info("gRPC-сервер запущен на порту {}: {}", port, services.stream().map(s -> s.getClass().getSimpleName()).toList());
        // не-демон поток удерживает JVM живой в сервисах без веб-сервера
        Thread keepAlive = new Thread(() -> {
            try {
                server.awaitTermination();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "grpc-await");
        keepAlive.setDaemon(false);
        keepAlive.start();
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
            log.info("gRPC-сервер остановлен");
        }
    }

    @Override
    public boolean isRunning() {
        return server != null && !server.isShutdown();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 10; // стартует последним, останавливается первым
    }
}
