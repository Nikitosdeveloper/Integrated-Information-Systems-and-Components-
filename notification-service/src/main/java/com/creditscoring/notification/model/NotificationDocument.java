package com.creditscoring.notification.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Уведомление. Адресат задаётся типом и ключом: USER (id пользователя), COMPANY (id компании), ROLE (код роли).
 * Состояние "прочитано" ведётся по пользователям (readBy): одно ролевое уведомление читают несколько сотрудников.
 */
@Document("notifications")
@CompoundIndex(name = "recipient_created", def = "{'recipientType': 1, 'recipientKey': 1, 'createdAt': -1}")
public class NotificationDocument {
    @Id
    public String id;            // eventId + суффикс: повторная доставка того же события не создаёт дубликат
    public String recipientType;
    public String recipientKey;
    public String type;
    public String title;
    public String message;
    public String applicationId;
    public Instant createdAt;
    public List<String> readBy = new ArrayList<>();
    public String deliveryStatus;
}
