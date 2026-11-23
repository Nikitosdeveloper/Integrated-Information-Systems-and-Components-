package com.creditscoring.scoring.kafka;

import com.creditscoring.common.kafka.EventConsumers;
import com.creditscoring.common.kafka.Topics;
import com.creditscoring.scoring.service.ScoringService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Kafka consumer: завершение анализа запускает скоринг заявки. */
@Component
public class AnalysisEventListener {
    private final ScoringService scoring;

    public AnalysisEventListener(ScoringService scoring) {
        this.scoring = scoring;
    }

    @KafkaListener(topics = Topics.ANALYSIS_EVENTS)
    public void onEvent(ConsumerRecord<String, String> record) {
        EventConsumers.handle("scoring-service", record, event -> {
            if (Topics.ANALYSIS_COMPLETED.equals(event.type())) {
                scoring.score(UUID.fromString(event.applicationId()), event.num("amount"));
            }
        });
    }
}
