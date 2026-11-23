package com.creditscoring.notification.service;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.kafka.DomainEvent;
import com.creditscoring.common.kafka.Topics;
import com.creditscoring.common.security.Actor;
import com.creditscoring.contracts.ListNotificationsRequest;
import com.creditscoring.contracts.Notification;
import com.creditscoring.contracts.NotificationList;
import com.creditscoring.notification.model.NotificationDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Map<String, String> STATUS_RU = Map.of("DRAFT", "Черновик", "REVIEW", "На рассмотрении",
            "WAITING_DOCS", "Ожидание документов", "COMMITTEE", "В кредитном комитете", "APPROVED", "Одобрена",
            "REJECTED", "Отклонена", "CANCELLED", "Отменена");

    private final MongoTemplate mongo;

    public NotificationService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    // ---------- создание по событиям ----------

    /** Формирует уведомления по событию. Идентификатор документа строится от eventId, поэтому повторная обработка безопасна. */
    public void onEvent(DomainEvent e) {
        switch (e.type()) {
            case Topics.APPLICATION_SUBMITTED -> save(e, "ROLE", "ANALYST", "a", "Новая заявка",
                    "Подана заявка компании «%s» на сумму %,.0f".formatted(e.str("companyName"), e.num("amount")));
            case Topics.STATUS_CHANGED -> {
                String from = e.str("fromStatus");
                String to = e.str("toStatus");
                save(e, "COMPANY", e.companyId(), "c", "Статус заявки изменён",
                        "Заявка: %s → %s".formatted(STATUS_RU.getOrDefault(from, from), STATUS_RU.getOrDefault(to, to)));
                if ("COMMITTEE".equals(to)) {
                    save(e, "ROLE", "COMMITTEE", "k", "Заявка передана в комитет", "Заявка ожидает кредитного решения");
                }
                if ("REVIEW".equals(to) && "WAITING_DOCS".equals(from)) {
                    save(e, "ROLE", "ANALYST", "a", "Документы предоставлены", "Заёмщик предоставил запрошенные документы");
                }
            }
            case Topics.DECISION_MADE -> save(e, "COMPANY", e.companyId(), "c", "Принято кредитное решение",
                    "APPROVED".equals(e.str("decision")) ? "Заявка одобрена" : "Заявка отклонена");
            case Topics.SCORING_COMPLETED -> save(e, "ROLE", "ANALYST", "a", "Скоринг рассчитан",
                    "Рейтинг заявки: %s (%.1f балла)".formatted(e.str("rating"), e.num("totalScore")));
            default -> log.debug("Событие {} не порождает уведомлений", e.type());
        }
    }

    private void save(DomainEvent e, String recipientType, String key, String suffix, String title, String message) {
        NotificationDocument d = new NotificationDocument();
        d.id = e.eventId() + "-" + suffix;
        d.recipientType = recipientType;
        d.recipientKey = key;
        d.type = e.type();
        d.title = title;
        d.message = message;
        d.applicationId = e.applicationId();
        d.createdAt = Instant.now();
        d.deliveryStatus = "SENT"; // отправка во внешний канал (e-mail/push) имитируется
        mongo.save(d);
        log.info("BUSINESS notification.created type={} recipient={}:{} application={}", e.type(), recipientType, key, e.applicationId());
    }

    // ---------- чтение ----------

    public NotificationList list(Actor actor, ListNotificationsRequest r) {
        int limit = r.getLimit() == 0 ? 20 : r.getLimit();
        if (limit < 1 || limit > 100) {
            throw AppException.badRequest("limit должен быть от 1 до 100");
        }
        Criteria visible = visibleTo(actor);
        Criteria c = r.getUnreadOnly() ? new Criteria().andOperator(visible, Criteria.where("readBy").ne(actor.userId())) : visible;
        List<NotificationDocument> docs = mongo.find(new Query(c).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit), NotificationDocument.class);
        return NotificationList.newBuilder().addAllItems(docs.stream().map(d -> toProto(d, actor)).toList()).build();
    }

    public Notification markRead(Actor actor, String id) {
        Query q = new Query(new Criteria().andOperator(Criteria.where("_id").is(id), visibleTo(actor)));
        NotificationDocument d = mongo.findOne(q, NotificationDocument.class);
        if (d == null) {
            throw AppException.notFound("Уведомление");
        }
        mongo.updateFirst(q, new Update().addToSet("readBy", actor.userId()), NotificationDocument.class);
        if (!d.readBy.contains(actor.userId())) {
            d.readBy.add(actor.userId());
        }
        return toProto(d, actor);
    }

    /** Пользователь видит личные уведомления, уведомления своей компании и уведомления своей роли. */
    private static Criteria visibleTo(Actor actor) {
        List<Criteria> any = new ArrayList<>();
        any.add(new Criteria().andOperator(Criteria.where("recipientType").is("USER"), Criteria.where("recipientKey").is(actor.userId())));
        any.add(new Criteria().andOperator(Criteria.where("recipientType").is("ROLE"), Criteria.where("recipientKey").is(actor.role())));
        if (actor.companyId() != null && !actor.companyId().isBlank()) {
            any.add(new Criteria().andOperator(Criteria.where("recipientType").is("COMPANY"), Criteria.where("recipientKey").is(actor.companyId())));
        }
        return new Criteria().orOperator(any.toArray(new Criteria[0]));
    }

    private static Notification toProto(NotificationDocument d, Actor actor) {
        return Notification.newBuilder().setId(d.id).setType(d.type).setTitle(d.title).setMessage(d.message)
                .setApplicationId(d.applicationId == null ? "" : d.applicationId).setCreatedAt(d.createdAt.toString())
                .setIsRead(d.readBy.contains(actor.userId())).setDeliveryStatus(d.deliveryStatus).build();
    }
}
