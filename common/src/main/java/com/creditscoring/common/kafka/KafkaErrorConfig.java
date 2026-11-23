package com.creditscoring.common.kafka;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Обработка ошибок Kafka-consumer: ограниченное число повторов с растущей паузой,
 * после чего сообщение переносится в топик "<топик>.DLT" и обработка продолжается со следующего.
 * Некорректные сообщения и бизнес-ошибки уходят в DLT без повторов.
 */
@Configuration
public class KafkaErrorConfig {
    private static final Logger log = LoggerFactory.getLogger("kafka.consumer");

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(4_000L); // не более 3-4 повторов
        DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
            log.error("KAFKA DLT topic={} offset={} -> {}.DLT: {}", record.topic(), record.offset(), record.topic(), ex.toString());
            recoverer.accept(record, ex);
        }, backOff);
        handler.addNotRetryableExceptions(EventConsumers.NonRetryableEventException.class);
        return handler;
    }
}
