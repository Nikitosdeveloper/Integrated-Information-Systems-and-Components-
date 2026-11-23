package com.creditscoring.common.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Единый формат сообщения Kafka (JSON). Тип события определяет состав data:
 * <pre>
 * APPLICATION_SUBMITTED / ANALYSIS_REQUESTED: companyId, industryCode, amount
 * STATUS_CHANGED:     fromStatus, toStatus, comment
 * DECISION_MADE:      decision, approvedAmount
 * ANALYSIS_COMPLETED: companyId, industryCode, amount
 * SCORING_COMPLETED:  rating, totalScore, recommendedLimit, stopTriggered
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DomainEvent(String eventId, String type, String applicationId, String companyId,
                          Map<String, Object> data, String correlationId, String occurredAt) {

    public String str(String key) {
        Object v = data == null ? null : data.get(key);
        return v == null ? null : v.toString();
    }

    public double num(String key) {
        Object v = data == null ? null : data.get(key);
        return v instanceof Number n ? n.doubleValue() : v == null ? 0 : Double.parseDouble(v.toString());
    }
}
