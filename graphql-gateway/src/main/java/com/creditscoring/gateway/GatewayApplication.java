package com.creditscoring.gateway;

import com.creditscoring.common.clients.AnalysisClient;
import com.creditscoring.common.clients.ApplicationClient;
import com.creditscoring.common.clients.BorrowerClient;
import com.creditscoring.common.clients.ClientsConfig;
import com.creditscoring.common.clients.NotificationClient;
import com.creditscoring.common.clients.ScoringClient;
import com.creditscoring.common.clients.StatementClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * GraphQL Gateway: защищённая внешняя точка входа. Проверяет JWT и сессию (через Auth Service),
 * определяет роль и передаёт выполнение внутренним сервисам по gRPC. Бизнес-логики и доступа к БД не содержит.
 */
@SpringBootApplication
@Import({ClientsConfig.class, BorrowerClient.class, ApplicationClient.class, StatementClient.class,
        AnalysisClient.class, ScoringClient.class, NotificationClient.class})
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
