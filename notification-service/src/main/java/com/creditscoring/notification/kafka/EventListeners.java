package com.creditscoring.notification.kafka;

import com.creditscoring.common.kafka.EventConsumers;
import com.creditscoring.common.kafka.Topics;
import com.creditscoring.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka consumer: события заявок и скоринга превращаются в уведомления. */
@Component
public class EventListeners {
    private final NotificationService notifications;

    public EventListeners(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(topics = {Topics.APPLICATION_EVENTS, Topics.SCORING_EVENTS})
    public void onEvent(ConsumerRecord<String, String> record) {
        EventConsumers.handle("notification-service", record, notifications::onEvent);
    }
}
