package com.creditscoring.common.grpc;

import io.grpc.BindableService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class GrpcServerConfig {

    @Bean
    public GrpcServerRunner grpcServerRunner(@Value("${grpc.server.port}") int port, List<BindableService> services) {
        return new GrpcServerRunner(port, services);
    }
}
