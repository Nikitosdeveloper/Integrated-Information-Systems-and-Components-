package com.creditscoring.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka producer: сериализует событие в JSON и публикует его в топик.
 * Внутри транзакции БД событие отправляется только после успешного commit.
 * Ошибка публикации фиксируется в логе и не откатывает уже выполненную бизнес-операцию.
 */
public class EventPublisher {
    private static final Logger log = LoggerFactory.getLogger("kafka.producer");
    public static final String CID_HEADER = "x-correlation-id";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String service;

    public EventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper, String service) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.service = service;
    }

    public void publish(String topic, String type, String applicationId, String companyId, Map<String, Object> data) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            String cid = MDC.get("cid");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(topic, type, applicationId, companyId, data, cid);
                }
            });
        } else {
            send(topic, type, applicationId, companyId, data, MDC.get("cid"));
        }
    }

    private void send(String topic, String type, String applicationId, String companyId, Map<String, Object> data, String cid) {
        DomainEvent event = new DomainEvent(UUID.randomUUID().toString(), type, applicationId, companyId, data, cid,
                OffsetDateTime.now().toString());
        try {
            String json = mapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, applicationId, json);
            if (cid != null) {
                record.headers().add(CID_HEADER, cid.getBytes(StandardCharsets.UTF_8));
            }
            kafka.send(record).whenComplete((result, error) -> {
                if (cid != null) {
                    MDC.put("cid", cid);
                }
                try {
                    if (error != null) {
                        log.error("KAFKA SEND FAILED service={} topic={} type={} application={}: {}", service, topic, type, applicationId, error.toString());
                    } else {
                        log.info("KAFKA SENT service={} topic={} partition={} offset={} type={} application={}", service, topic,
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset(), type, applicationId);
                    }
                } finally {
                    MDC.remove("cid");
                }
            });
        } catch (Exception e) {
            log.error("KAFKA SEND FAILED service={} topic={} type={} application={}: {}", service, topic, type, applicationId, e.toString());
        }
    }
}
