package com.creditscoring.scoring;

import com.creditscoring.common.cache.ProtoCacheConfig;
import com.creditscoring.common.clients.AnalysisClient;
import com.creditscoring.common.clients.ClientsConfig;
import com.creditscoring.common.grpc.GrpcServerConfig;
import com.creditscoring.common.kafka.KafkaErrorConfig;
import com.creditscoring.common.kafka.KafkaPublisherConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Scoring Service: скоринговые модели и расчёт кредитного рейтинга заявки. */
@SpringBootApplication
@Import({GrpcServerConfig.class, ProtoCacheConfig.class, KafkaPublisherConfig.class, KafkaErrorConfig.class,
        ClientsConfig.class, AnalysisClient.class})
public class ScoringServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScoringServiceApplication.class, args);
    }
}
