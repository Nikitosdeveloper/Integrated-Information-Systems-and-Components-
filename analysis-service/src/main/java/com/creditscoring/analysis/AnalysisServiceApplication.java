package com.creditscoring.analysis;

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

/** Analysis Service: расчёт финансовых коэффициентов и их оценка по отраслевым нормативам. */
@SpringBootApplication
@Import({GrpcServerConfig.class, ProtoCacheConfig.class, KafkaPublisherConfig.class, KafkaErrorConfig.class,
        ClientsConfig.class, StatementClient.class, BorrowerClient.class})
public class AnalysisServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalysisServiceApplication.class, args);
    }
}
