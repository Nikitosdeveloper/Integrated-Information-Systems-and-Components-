package com.creditscoring.notification;

import com.creditscoring.common.grpc.GrpcServerConfig;
import com.creditscoring.common.kafka.KafkaErrorConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Notification Service: формирует уведомления по событиям Kafka и отдаёт их пользователям (хранилище - MongoDB). */
@SpringBootApplication
@Import({GrpcServerConfig.class, KafkaErrorConfig.class})
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
