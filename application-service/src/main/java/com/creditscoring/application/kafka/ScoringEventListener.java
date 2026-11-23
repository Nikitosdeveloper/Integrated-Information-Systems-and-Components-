package com.creditscoring.application.kafka;

import com.creditscoring.application.service.ApplicationCommandService;
import com.creditscoring.common.kafka.EventConsumers;
import com.creditscoring.common.kafka.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Kafka consumer: результат скоринга записывается в заявку. */
@Component
public class ScoringEventListener {
    private final ApplicationCommandService commands;

    public ScoringEventListener(ApplicationCommandService commands) {
        this.commands = commands;
    }

    @KafkaListener(topics = Topics.SCORING_EVENTS)
    public void onEvent(ConsumerRecord<String, String> record) {
        EventConsumers.handle("application-service", record, event -> {
            if (Topics.SCORING_COMPLETED.equals(event.type())) {
                commands.applyScoring(UUID.fromString(event.applicationId()), event.str("rating"), event.num("totalScore"),
                        event.num("recommendedLimit"));
            }
        });
    }
}
