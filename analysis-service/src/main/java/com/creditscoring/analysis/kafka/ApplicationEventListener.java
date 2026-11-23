package com.creditscoring.analysis.kafka;

import com.creditscoring.analysis.service.AnalysisService;
import com.creditscoring.common.kafka.EventConsumers;
import com.creditscoring.common.kafka.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Kafka consumer: подача заявки (или запрос пересчёта) запускает расчёт коэффициентов. */
@Component
public class ApplicationEventListener {
    private final AnalysisService analysis;

    public ApplicationEventListener(AnalysisService analysis) {
        this.analysis = analysis;
    }

    @KafkaListener(topics = Topics.APPLICATION_EVENTS)
    public void onEvent(ConsumerRecord<String, String> record) {
        EventConsumers.handle("analysis-service", record, event -> {
            if (Topics.APPLICATION_SUBMITTED.equals(event.type()) || Topics.ANALYSIS_REQUESTED.equals(event.type())) {
                analysis.analyze(UUID.fromString(event.applicationId()), UUID.fromString(event.str("companyId")),
                        event.str("industryCode"), event.num("amount"));
            }
            // остальные типы событий для сервиса не интересны
        });
    }
}
