package com.creditscoring.application;

import com.creditscoring.common.cache.ProtoCacheConfig;
import com.creditscoring.common.clients.BorrowerClient;
import com.creditscoring.common.clients.ClientsConfig;
import com.creditscoring.common.clients.StatementClient;
import com.creditscoring.common.grpc.GrpcServerConfig;
import com.creditscoring.common.kafka.KafkaErrorConfig;
import com.creditscoring.common.kafka.KafkaPublisherConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Application Service: кредитные продукты, заявки и их жизненный цикл, запросы документов, заключение и решение. */
@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
@Import({GrpcServerConfig.class, ProtoCacheConfig.class, KafkaPublisherConfig.class, KafkaErrorConfig.class,
        ClientsConfig.class, BorrowerClient.class, StatementClient.class})
public class ApplicationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApplicationServiceApplication.class, args);
    }
}
