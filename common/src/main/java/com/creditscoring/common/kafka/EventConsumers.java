package com.creditscoring.common.kafka;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.error.ErrorCode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.function.Consumer;

/** Общая обработка входящего сообщения: десериализация, correlation id, логирование, классификация ошибок. */
public final class EventConsumers {
    private static final Logger log = LoggerFactory.getLogger("kafka.consumer");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Ошибка, повтор которой бессмысленен (битый payload, нарушено бизнес-правило): сразу в DLT. */
    public static class NonRetryableEventException extends RuntimeException {
        public NonRetryableEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static void handle(String service, ConsumerRecord<String, String> record, Consumer<DomainEvent> handler) {
        DomainEvent event;
        try {
            event = MAPPER.readValue(record.value(), DomainEvent.class);
        } catch (Exception e) {
            log.error("KAFKA RECV {} topic={} partition={} offset={}: payload не разобран, сообщение уходит в DLT",
                    service, record.topic(), record.partition(), record.offset());
            throw new NonRetryableEventException("Некорректный payload сообщения", e);
        }
        if (event.correlationId() != null) {
            MDC.put("cid", event.correlationId());
        }
        try {
            log.info("KAFKA RECV service={} topic={} partition={} offset={} type={} application={}", service, record.topic(),
                    record.partition(), record.offset(), event.type(), event.applicationId());
            handler.accept(event);
            log.info("KAFKA DONE service={} type={} application={}", service, event.type(), event.applicationId());
        } catch (AppException e) {
            boolean transientError = e.code() == ErrorCode.SERVICE_UNAVAILABLE || e.code() == ErrorCode.TIMEOUT;
            log.error("KAFKA FAIL service={} type={} application={} code={}: {}{}", service, event.type(), event.applicationId(),
                    e.code(), e.getMessage(), transientError ? " (временная ошибка, будет повтор)" : " (повтор не нужен)");
            if (transientError) {
                throw e;
            }
            throw new NonRetryableEventException(e.getMessage(), e);
        } finally {
            MDC.remove("cid");
        }
    }

    private EventConsumers() {
    }
}
