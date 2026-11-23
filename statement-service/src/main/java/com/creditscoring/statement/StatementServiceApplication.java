package com.creditscoring.statement;

import com.creditscoring.common.grpc.GrpcServerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Statement Service: финансовая отчётность компаний (баланс, отчёт о прибылях) и её контроль. */
@SpringBootApplication
@Import(GrpcServerConfig.class)
public class StatementServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StatementServiceApplication.class, args);
    }
}
