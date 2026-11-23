package com.creditscoring.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class KafkaPublisherConfig {

    @Bean
    public EventPublisher eventPublisher(KafkaTemplate<String, String> kafka,
                                         @Value("${spring.application.name}") String service) {
        return new EventPublisher(kafka, new ObjectMapper(), service);
    }
}
