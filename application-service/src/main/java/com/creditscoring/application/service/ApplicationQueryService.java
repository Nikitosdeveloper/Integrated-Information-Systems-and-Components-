package com.creditscoring.application.service;

import com.creditscoring.application.entity.CreditApplication;
import com.creditscoring.application.repository.CreditApplicationRepository;
import com.creditscoring.application.repository.CreditProductRepository;
import com.creditscoring.application.repository.Repos;
import com.creditscoring.common.cache.ProtoCache;
import com.creditscoring.common.error.AppException;
import com.creditscoring.common.security.Actor;
import com.creditscoring.common.security.Roles;
import com.creditscoring.contracts.AnalystConclusion;
import com.creditscoring.contracts.ApplicationFilter;
import com.creditscoring.contracts.ApplicationPage;
import com.creditscoring.contracts.ApplicationStats;
import com.creditscoring.contracts.ApplicationStatsRequest;
import com.creditscoring.contracts.CreditDecision;
import com.creditscoring.contracts.DocumentRequestList;
import com.creditscoring.contracts.HistoryList;
import com.creditscoring.contracts.ListApplicationsRequest;
import com.creditscoring.contracts.ProductList;
import com.creditscoring.contracts.StatusStat;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Операции чтения: карточка, списки и очереди с фильтрацией, сортировкой и пагинацией, агрегирующая статистика. */
@Service
@Transactional(readOnly = true)
public class ApplicationQueryService {
    static final Set<String> STATUSES = Set.of("DRAFT", "REVIEW", "WAITING_DOCS", "COMMITTEE", "APPROVED", "REJECTED", "CANCELLED");
    private static final Map<String, String> SORT_FIELDS = Map.of("CREATED_AT", "createdAt", "SUBMITTED_AT", "submittedAt",
            "AMOUNT", "amount", "STATUS", "status", "SCORE", "latestScore");
    private static final int MAX_PAGE_SIZE = 100;

    private final CreditApplicationRepository applications;
    private final CreditProductRepository products;
    private final Repos.StatusHistoryRepository history;
    private final Repos.DocumentRequestRepository documentRequests;
    private final Repos.AnalystConclusionRepository conclusions;
    private final Repos.CreditDecisionRepository decisions;
    private final ProtoCache cache;
    private final EntityManager em;

    public ApplicationQueryService(CreditApplicationRepository applications, CreditProductRepository products,
                                   Repos.StatusHistoryRepository history, Repos.DocumentRequestRepository documentRequests,
                                   Repos.AnalystConclusionRepository conclusions, Repos.CreditDecisionRepository decisions,
                                   ProtoCache cache, EntityManager em) {
        this.applications = applications;
        this.products = products;
        this.history = history;
        this.documentRequests = documentRequests;
        this.conclusions = conclusions;
        this.decisions = decisions;
        this.cache = cache;
        this.em = em;
    }

    /** Справочник продуктов - частые чтения при создании заявки, редкие изменения: кеш Redis. */
    public ProductList listProducts(boolean includeArchived) {
        return cache.getOrLoad("application:products:" + (includeArchived ? "all" : "active"), ProductList.getDefaultInstance(),
                Duration.ofMinutes(10), () -> ProductList.newBuilder().addAllItems((includeArchived
                        ? products.findAllByOrderByName() : products.findByStatusOrderByName("ACTIVE")).stream().map(Mapper::toProto).toList()).build());
    }

    public com.creditscoring.contracts.CreditApplication get(Actor actor, UUID id) {
        return Mapper.toProto(load(actor, id));
    }

    public ApplicationPage list(Actor actor, ListApplicationsRequest r) {
        int page = r.getPage() == 0 ? 1 : r.getPage();
        int size = r.getPageSize() == 0 ? 20 : r.getPageSize();
        if (page < 1) {
            throw AppException.badRequest("page должен быть >= 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw AppException.badRequest("pageSize должен быть от 1 до " + MAX_PAGE_SIZE);
        }
        Specification<CreditApplication> spec = specification(actor, r.getScope(), r.getFilter());
        Page<CreditApplication> res = applications.findAll(spec, PageRequest.of(page - 1, size, sort(r.getSortField(), r.getSortDirection())));
        return ApplicationPage.newBuilder().addAllItems(res.getContent().stream().map(Mapper::toProto).toList())
                .setTotal(res.getTotalElements()).setPage(page).setPageSize(size).build();
    }

    /** Агрегация по тем же правилам доступа и фильтрам, что и список: количество и сумма по статусам, средняя сумма и средний балл. */
    public ApplicationStats stats(Actor actor, ApplicationStatsRequest r) {
        Specification<CreditApplication> spec = specification(actor, r.getScope(), r.getFilter());
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Tuple> byStatus = cb.createTupleQuery();
        Root<CreditApplication> root = byStatus.from(CreditApplication.class);
        byStatus.multiselect(root.get("status"), cb.count(root), cb.sum(root.<BigDecimal>get("amount"))).groupBy(root.get("status"));
        Predicate p = spec.toPredicate(root, byStatus, cb);
        if (p != null) {
            byStatus.where(p);
        }
        var out = ApplicationStats.newBuilder();
        long totalCount = 0;
        double totalAmount = 0;
        for (Tuple t : em.createQuery(byStatus).getResultList()) {
            long count = t.get(1, Long.class);
            double sum = t.get(2, BigDecimal.class) == null ? 0 : t.get(2, BigDecimal.class).doubleValue();
            out.addByStatus(StatusStat.newBuilder().setStatus(t.get(0, String.class)).setCount(count).setTotalAmount(sum));
            totalCount += count;
            totalAmount += sum;
        }
        out.setTotalCount(totalCount).setTotalAmount(totalAmount);

        CriteriaQuery<Tuple> averages = cb.createTupleQuery();
        Root<CreditApplication> root2 = averages.from(CreditApplication.class);
        averages.multiselect(cb.avg(root2.<BigDecimal>get("amount")), cb.avg(root2.<BigDecimal>get("latestScore")));
        Predicate p2 = spec.toPredicate(root2, averages, cb);
        if (p2 != null) {
            averages.where(p2);
        }
        Tuple avg = em.createQuery(averages).getSingleResult();
        out.setAverageAmount(avg.get(0) == null ? 0 : ((Number) avg.get(0)).doubleValue());
        if (avg.get(1) != null) {
            out.setAverageScore(((Number) avg.get(1)).doubleValue());
        }
        return out.build();
    }

    public HistoryList history(Actor actor, UUID applicationId) {
        load(actor, applicationId);
        return HistoryList.newBuilder().addAllItems(history.findByApplicationIdOrderByChangedAtAsc(applicationId).stream().map(Mapper::toProto).toList()).build();
    }

    public DocumentRequestList documentRequests(Actor actor, UUID applicationId) {
        load(actor, applicationId);
        return DocumentRequestList.newBuilder().addAllItems(documentRequests.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream().map(Mapper::toProto).toList()).build();
    }

    public AnalystConclusion conclusion(Actor actor, UUID applicationId) {
        actor.requireRole(Roles.ANALYST, Roles.COMMITTEE, Roles.ADMIN, Roles.SYSTEM);
        return conclusions.findByApplicationId(applicationId).map(Mapper::toProto).orElseThrow(() -> AppException.notFound("Заключение аналитика"));
    }

    public CreditDecision decision(Actor actor, UUID applicationId) {
        load(actor, applicationId);
        return decisions.findByApplicationId(applicationId).map(Mapper::toProto).orElseThrow(() -> AppException.notFound("Кредитное решение"));
    }

    // ---------- общие правила доступа ----------

    /** Загрузка заявки с проверкой доступа: заёмщик видит только заявки своей компании. */
    CreditApplication load(Actor actor, UUID id) {
        CreditApplication a = applications.findById(id).orElseThrow(() -> AppException.notFound("Заявка"));
        if (actor.isBorrower() && !a.getCompanyId().toString().equals(actor.companyId())) {
            throw AppException.forbidden("Доступ к чужой заявке запрещён");
        }
        return a;
    }

    // ---------- фильтрация и сортировка ----------

    private Specification<CreditApplication> specification(Actor actor, String scope, ApplicationFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            switch (scope) {
                case "MY" -> {
                    actor.requireRole(Roles.BORROWER);
                    p.add(cb.equal(root.get("companyId"), actor.companyUuid()));
                }
                case "ANALYST_QUEUE" -> {
                    actor.requireRole(Roles.ANALYST);
                    UUID me = actor.userUuid();
                    // свои заявки + неназначенные заявки на рассмотрении
                    p.add(cb.or(cb.equal(root.get("assignedAnalystId"), me),
                            cb.and(cb.isNull(root.get("assignedAnalystId")), cb.equal(root.get("status"), "REVIEW"))));
                }
                case "COMMITTEE_QUEUE" -> {
                    actor.requireRole(Roles.COMMITTEE);
                    p.add(cb.equal(root.get("status"), "COMMITTEE"));
                }
                case "ALL" -> {
                    actor.requireRole(Roles.ANALYST, Roles.COMMITTEE, Roles.ADMIN);
                    if (!actor.hasRole(Roles.ADMIN)) {
                        p.add(cb.notEqual(root.get("status"), "DRAFT")); // черновики видит только заёмщик (и администратор)
                    }
                }
                default -> throw AppException.badRequest("Неизвестная область списка: " + scope);
            }
            if (f.getStatusesCount() > 0) {
                for (String s : f.getStatusesList()) {
                    if (!STATUSES.contains(s)) {
                        throw AppException.badRequest("Неизвестный статус: " + s);
                    }
                }
                p.add(root.get("status").in(f.getStatusesList()));
            }
            if (f.hasAmountFrom()) {
                p.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("amount"), BigDecimal.valueOf(f.getAmountFrom())));
            }
            if (f.hasAmountTo()) {
                p.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("amount"), BigDecimal.valueOf(f.getAmountTo())));
            }
            if (f.hasAmountFrom() && f.hasAmountTo() && f.getAmountFrom() > f.getAmountTo()) {
                throw AppException.badRequest("amountFrom не может быть больше amountTo");
            }
            if (!f.getCompanyNameContains().isBlank()) {
                String like = "%" + f.getCompanyNameContains().trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.like(cb.lower(root.get("companyName")), like, '\\'));
            }
            if (!f.getRating().isBlank()) {
                p.add(cb.equal(root.get("latestRating"), f.getRating()));
            }
            if (!f.getCreatedFrom().isBlank()) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), date(f.getCreatedFrom()).atStartOfDay().atOffset(ZoneOffset.UTC)));
            }
            if (!f.getCreatedTo().isBlank()) {
                p.add(cb.lessThan(root.get("createdAt"), date(f.getCreatedTo()).plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)));
            }
            if (f.getAssignedToMe()) {
                actor.requireRole(Roles.ANALYST);
                p.add(cb.equal(root.get("assignedAnalystId"), actor.userUuid()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private static LocalDate date(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw AppException.badRequest("Дата должна быть в формате ГГГГ-ММ-ДД: " + s);
        }
    }

    private static Sort sort(String field, String direction) {
        String property = field.isBlank() ? "createdAt" : SORT_FIELDS.get(field);
        if (property == null) {
            throw AppException.badRequest("Неизвестное поле сортировки: " + field);
        }
        if (!direction.isBlank() && !direction.equals("ASC") && !direction.equals("DESC")) {
            throw AppException.badRequest("Направление сортировки: ASC или DESC");
        }
        Sort.Direction dir = "ASC".equals(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // вторичный ключ делает порядок стабильным при постраничной выдаче
        return Sort.by(new Sort.Order(dir, property).nullsLast(), new Sort.Order(Sort.Direction.ASC, "id"));
    }
}
