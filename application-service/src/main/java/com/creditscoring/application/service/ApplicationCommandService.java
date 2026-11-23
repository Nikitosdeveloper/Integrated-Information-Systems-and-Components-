package com.creditscoring.application.service;

import com.creditscoring.application.entity.AnalystConclusion;
import com.creditscoring.application.entity.CreditApplication;
import com.creditscoring.application.entity.CreditDecision;
import com.creditscoring.application.entity.CreditProduct;
import com.creditscoring.application.entity.DocumentRequest;
import com.creditscoring.application.entity.DocumentRequestItem;
import com.creditscoring.application.entity.StatusHistory;
import com.creditscoring.application.repository.CreditApplicationRepository;
import com.creditscoring.application.repository.CreditProductRepository;
import com.creditscoring.application.repository.Repos;
import com.creditscoring.common.clients.BorrowerClient;
import com.creditscoring.common.clients.StatementClient;
import com.creditscoring.common.error.AppException;
import com.creditscoring.common.kafka.EventPublisher;
import com.creditscoring.common.kafka.Topics;
import com.creditscoring.common.security.Actor;
import com.creditscoring.common.security.Roles;
import com.creditscoring.contracts.CancelApplicationRequest;
import com.creditscoring.contracts.CreateApplicationRequest;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.ListCompanyStatementsRequest;
import com.creditscoring.contracts.MakeDecisionRequest;
import com.creditscoring.contracts.ProvideDocumentRequest;
import com.creditscoring.contracts.RequestDocumentsRequest;
import com.creditscoring.contracts.RequestedItem;
import com.creditscoring.contracts.SaveConclusionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Бизнес-операции над заявкой. Все проверки (роль, принадлежность, текущий статус, входные данные) выполняются
 * до изменения состояния; переходы статусов проходят через единую таблицу допустимых переходов.
 * Каждое изменение статуса попадает в историю и публикуется событием в Kafka (после commit).
 */
@Service
public class ApplicationCommandService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationCommandService.class);

    /** Допустимые переходы статусов. Финальные статусы (APPROVED, REJECTED, CANCELLED) переходов не имеют. */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "DRAFT", Set.of("REVIEW", "CANCELLED"),
            "REVIEW", Set.of("WAITING_DOCS", "COMMITTEE", "CANCELLED"),
            "WAITING_DOCS", Set.of("REVIEW", "CANCELLED"),
            "COMMITTEE", Set.of("APPROVED", "REJECTED"));
    private static final int MAX_ACTIVE_APPLICATIONS = 5;
    private static final Set<String> FILE_EXTENSIONS = Set.of("pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png");

    private final CreditApplicationRepository applications;
    private final CreditProductRepository products;
    private final Repos.StatusHistoryRepository history;
    private final Repos.DocumentRequestRepository requests;
    private final Repos.DocumentRequestItemRepository requestItems;
    private final Repos.AnalystConclusionRepository conclusions;
    private final Repos.CreditDecisionRepository decisions;
    private final ApplicationQueryService queries;
    private final BorrowerClient borrowers;
    private final StatementClient statements;
    private final EventPublisher events;

    public ApplicationCommandService(CreditApplicationRepository applications, CreditProductRepository products,
                                     Repos.StatusHistoryRepository history, Repos.DocumentRequestRepository requests,
                                     Repos.DocumentRequestItemRepository requestItems, Repos.AnalystConclusionRepository conclusions,
                                     Repos.CreditDecisionRepository decisions, ApplicationQueryService queries,
                                     BorrowerClient borrowers, StatementClient statements, EventPublisher events) {
        this.applications = applications;
        this.products = products;
        this.history = history;
        this.requests = requests;
        this.requestItems = requestItems;
        this.conclusions = conclusions;
        this.decisions = decisions;
        this.queries = queries;
        this.borrowers = borrowers;
        this.statements = statements;
        this.events = events;
    }

    // ---------- заёмщик ----------

    @Transactional
    public com.creditscoring.contracts.CreditApplication create(Actor actor, CreateApplicationRequest r) {
        actor.requireRole(Roles.BORROWER);
        UUID companyId = actor.companyUuid();
        CreditProduct product = products.findById(parse(r.getProductId())).orElseThrow(() -> AppException.notFound("Кредитный продукт"));
        if (!"ACTIVE".equals(product.getStatus())) {
            throw AppException.conflict("Кредитный продукт «" + product.getName() + "» недоступен для новых заявок");
        }
        BigDecimal amount = BigDecimal.valueOf(r.getAmount());
        if (amount.compareTo(product.getMinAmount()) < 0 || amount.compareTo(product.getMaxAmount()) > 0) {
            throw AppException.badRequest("Сумма должна быть в диапазоне %s - %s".formatted(product.getMinAmount().toPlainString(), product.getMaxAmount().toPlainString()));
        }
        if (r.getTermMonths() < 1 || r.getTermMonths() > product.getMaxTermMonths()) {
            throw AppException.badRequest("Срок должен быть от 1 до " + product.getMaxTermMonths() + " месяцев");
        }
        if (r.getPurpose().isBlank() || r.getPurpose().length() > 1000) {
            throw AppException.badRequest("Укажите цель кредита (до 1000 символов)");
        }
        if (applications.countByCompanyIdAndStatusIn(companyId, Set.of("DRAFT", "REVIEW", "WAITING_DOCS", "COMMITTEE")) >= MAX_ACTIVE_APPLICATIONS) {
            throw AppException.conflict("Превышено допустимое число незавершённых заявок (" + MAX_ACTIVE_APPLICATIONS + ")");
        }
        // название и отрасль компании хранит Borrower Service: запрос по gRPC
        var company = borrowers.getCompany(actor, IdRequest.newBuilder().setId(companyId.toString()).build());

        CreditApplication a = new CreditApplication();
        a.setId(UUID.randomUUID());
        a.setCompanyId(companyId);
        a.setCompanyName(company.getName());
        a.setIndustryCode(company.getIndustryCode());
        a.setProduct(product);
        a.setAmount(amount);
        a.setTermMonths(r.getTermMonths());
        a.setPurpose(r.getPurpose().trim());
        a.setCollateral(r.getCollateral().isBlank() ? null : r.getCollateral().trim());
        a.setStatus("DRAFT");
        a.setCreatedBy(actor.userUuid());
        a.setCreatedAt(OffsetDateTime.now());
        applications.save(a);
        history.save(new StatusHistory(a.getId(), null, "DRAFT", actor.userUuid(), "Заявка создана"));
        log.info("BUSINESS application.create id={} company={} amount={} by={}", a.getId(), a.getCompanyName(), amount, actor.email());
        return Mapper.toProto(a);
    }

    /**
     * Подача заявки: комплект отчётности проверяется у Statement Service по gRPC.
     * Дальше цепочка асинхронная: событие -> Analysis (коэффициенты) -> Scoring (рейтинг) -> обновление заявки и уведомления.
     */
    @Transactional
    public com.creditscoring.contracts.CreditApplication submit(Actor actor, UUID id) {
        actor.requireRole(Roles.BORROWER);
        CreditApplication a = queries.load(actor, id);
        requireStatus(a, "DRAFT");
        var statementList = statements.listCompanyStatements(actor, ListCompanyStatementsRequest.newBuilder()
                .setCompanyId(a.getCompanyId().toString()).setOnlyValid(true).build());
        Map<Integer, Set<String>> forms = new HashMap<>();
        statementList.getItemsList().forEach(s -> forms.computeIfAbsent(s.getPeriodYear(), y -> new java.util.HashSet<>()).add(s.getFormType()));
        boolean complete = forms.values().stream().anyMatch(f -> f.contains("BALANCE") && f.contains("PROFIT_LOSS"));
        if (!complete) {
            throw AppException.conflict("Для подачи заявки загрузите корректные баланс и отчёт о прибылях за один отчётный год");
        }
        a.setSubmittedAt(OffsetDateTime.now());
        changeStatus(a, "REVIEW", actor, "Заявка подана");
        events.publish(Topics.APPLICATION_EVENTS, Topics.APPLICATION_SUBMITTED, a.getId().toString(), a.getCompanyId().toString(), analysisPayload(a));
        return Mapper.toProto(a);
    }

    @Transactional
    public com.creditscoring.contracts.CreditApplication cancel(Actor actor, CancelApplicationRequest r) {
        actor.requireRole(Roles.BORROWER);
        CreditApplication a = queries.load(actor, parse(r.getId()));
        if (r.getReason().length() > 500) {
            throw AppException.badRequest("Причина не должна превышать 500 символов");
        }
        changeStatus(a, "CANCELLED", actor, r.getReason().isBlank() ? "Заявка отменена заёмщиком" : r.getReason().trim());
        requests.findByApplicationIdAndStatus(a.getId(), "OPEN").ifPresent(req -> req.setStatus("CLOSED"));
        return Mapper.toProto(a);
    }

    @Transactional
    public com.creditscoring.contracts.DocumentRequest provideDocument(Actor actor, ProvideDocumentRequest r) {
        actor.requireRole(Roles.BORROWER);
        DocumentRequestItem item = requestItems.findById(parse(r.getItemId())).orElseThrow(() -> AppException.notFound("Позиция запроса документов"));
        DocumentRequest req = item.getRequest();
        CreditApplication a = queries.load(actor, req.getApplicationId());
        requireStatus(a, "WAITING_DOCS");
        if (!"OPEN".equals(req.getStatus())) {
            throw AppException.conflict("Запрос документов закрыт");
        }
        if ("PROVIDED".equals(item.getStatus())) {
            throw AppException.conflict("Документ по этой позиции уже предоставлен");
        }
        String name = r.getFileName().trim();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (name.isEmpty() || name.length() > 255 || !FILE_EXTENSIONS.contains(ext)) {
            throw AppException.badRequest("Допустимые форматы файла: " + String.join(", ", new java.util.TreeSet<>(FILE_EXTENSIONS)));
        }
        item.setFileName(name);
        item.setStatus("PROVIDED");
        boolean allRequiredProvided = req.getItems().stream().filter(DocumentRequestItem::isRequired).allMatch(i -> "PROVIDED".equals(i.getStatus()));
        if (allRequiredProvided) {
            req.setStatus("CLOSED");
            changeStatus(a, "REVIEW", actor, "Предоставлены все обязательные документы");
        }
        log.info("BUSINESS application.provideDocument application={} item={} file={} requestClosed={}", a.getId(), item.getTitle(), name, allRequiredProvided);
        return Mapper.toProto(req);
    }

    // ---------- аналитик ----------

    @Transactional
    public com.creditscoring.contracts.CreditApplication assignToMe(Actor actor, UUID id) {
        actor.requireRole(Roles.ANALYST);
        CreditApplication a = queries.load(actor, id);
        requireStatus(a, "REVIEW");
        if (a.getAssignedAnalystId() != null && !a.getAssignedAnalystId().equals(actor.userUuid())) {
            throw AppException.conflict("Заявка уже назначена другому аналитику");
        }
        if (a.getAssignedAnalystId() == null) {
            a.setAssignedAnalystId(actor.userUuid());
            history.save(new StatusHistory(a.getId(), "REVIEW", "REVIEW", actor.userUuid(), "Заявка взята в работу аналитиком"));
            log.info("BUSINESS application.assign id={} analyst={}", id, actor.email());
        }
        return Mapper.toProto(a);
    }

    @Transactional
    public com.creditscoring.contracts.DocumentRequest requestDocuments(Actor actor, RequestDocumentsRequest r) {
        actor.requireRole(Roles.ANALYST);
        CreditApplication a = ownedByAnalyst(actor, parse(r.getApplicationId()));
        requireStatus(a, "REVIEW");
        if (r.getItemsCount() < 1 || r.getItemsCount() > 10) {
            throw AppException.badRequest("В запросе должно быть от 1 до 10 документов");
        }
        for (RequestedItem i : r.getItemsList()) {
            if (i.getTitle().isBlank() || i.getTitle().length() > 200) {
                throw AppException.badRequest("Название документа обязательно (до 200 символов)");
            }
        }
        if (r.getItemsList().stream().noneMatch(RequestedItem::getIsRequired)) {
            throw AppException.badRequest("Хотя бы один документ должен быть обязательным");
        }
        LocalDate due;
        try {
            due = LocalDate.parse(r.getDueDate());
        } catch (DateTimeParseException e) {
            throw AppException.badRequest("Срок предоставления должен быть в формате ГГГГ-ММ-ДД");
        }
        if (!due.isAfter(LocalDate.now()) || due.isAfter(LocalDate.now().plusDays(60))) {
            throw AppException.badRequest("Срок предоставления должен быть не раньше завтрашнего дня и не позже чем через 60 дней");
        }
        if (requests.findByApplicationIdAndStatus(a.getId(), "OPEN").isPresent()) {
            throw AppException.conflict("По заявке уже есть открытый запрос документов");
        }
        DocumentRequest req = new DocumentRequest();
        req.setId(UUID.randomUUID());
        req.setApplicationId(a.getId());
        req.setRequestedBy(actor.userUuid());
        req.setDueDate(due);
        req.setStatus("OPEN");
        req.setCreatedAt(OffsetDateTime.now());
        for (RequestedItem i : r.getItemsList()) {
            DocumentRequestItem item = new DocumentRequestItem();
            item.setId(UUID.randomUUID());
            item.setRequest(req);
            item.setTitle(i.getTitle().trim());
            item.setRequired(i.getIsRequired());
            item.setStatus("PENDING");
            req.getItems().add(item);
        }
        requests.save(req);
        changeStatus(a, "WAITING_DOCS", actor, "Запрошены документы");
        return Mapper.toProto(req);
    }

    @Transactional
    public com.creditscoring.contracts.AnalystConclusion saveConclusion(Actor actor, SaveConclusionRequest r) {
        actor.requireRole(Roles.ANALYST);
        CreditApplication a = ownedByAnalyst(actor, parse(r.getApplicationId()));
        requireStatus(a, "REVIEW");
        if (a.getLatestRating() == null) {
            throw AppException.conflict("Скоринг по заявке ещё не рассчитан: заключение можно сохранить после расчёта рейтинга");
        }
        String text = r.getText().trim();
        if (text.length() < 10 || text.length() > 4000) {
            throw AppException.badRequest("Текст заключения должен содержать от 10 до 4000 символов");
        }
        if (!r.getRecommendation().equals("APPROVE") && !r.getRecommendation().equals("REJECT")) {
            throw AppException.badRequest("Рекомендация: APPROVE или REJECT");
        }
        AnalystConclusion c = conclusions.findByApplicationId(a.getId()).orElseGet(() -> {
            AnalystConclusion n = new AnalystConclusion();
            n.setId(UUID.randomUUID());
            n.setApplicationId(a.getId());
            return n;
        });
        c.setAnalystId(actor.userUuid());
        c.setText(text);
        c.setRecommendation(r.getRecommendation());
        c.setCreatedAt(OffsetDateTime.now());
        conclusions.save(c);
        log.info("BUSINESS application.conclusion id={} recommendation={} by={}", a.getId(), r.getRecommendation(), actor.email());
        return Mapper.toProto(c);
    }

    @Transactional
    public com.creditscoring.contracts.CreditApplication sendToCommittee(Actor actor, UUID id) {
        actor.requireRole(Roles.ANALYST);
        CreditApplication a = ownedByAnalyst(actor, id);
        requireStatus(a, "REVIEW");
        if (conclusions.findByApplicationId(id).isEmpty()) {
            throw AppException.conflict("Перед передачей в комитет необходимо сохранить заключение аналитика");
        }
        changeStatus(a, "COMMITTEE", actor, "Передано в кредитный комитет");
        return Mapper.toProto(a);
    }

    /** Повторный запуск цепочки анализа и скоринга (например, после загрузки новой отчётности или смены модели). */
    @Transactional
    public void recalculateScoring(Actor actor, UUID id) {
        actor.requireRole(Roles.ANALYST, Roles.ADMIN);
        CreditApplication a = queries.load(actor, id);
        if (Set.of("DRAFT", "CANCELLED").contains(a.getStatus())) {
            throw AppException.conflict("Для заявки в статусе " + a.getStatus() + " расчёт недоступен");
        }
        events.publish(Topics.APPLICATION_EVENTS, Topics.ANALYSIS_REQUESTED, a.getId().toString(), a.getCompanyId().toString(), analysisPayload(a));
        log.info("BUSINESS application.recalculate id={} by={}", id, actor.email());
    }

    // ---------- кредитный комитет ----------

    @Transactional
    public com.creditscoring.contracts.CreditDecision makeDecision(Actor actor, MakeDecisionRequest r) {
        actor.requireRole(Roles.COMMITTEE);
        CreditApplication a = queries.load(actor, parse(r.getApplicationId()));
        requireStatus(a, "COMMITTEE");
        String decision = r.getDecision();
        String comment = r.getComment().trim();
        BigDecimal approved = null;
        if ("APPROVED".equals(decision)) {
            if (!r.hasApprovedAmount() || r.getApprovedAmount() <= 0) {
                throw AppException.badRequest("Для одобрения укажите одобренную сумму");
            }
            approved = BigDecimal.valueOf(r.getApprovedAmount());
            if (approved.compareTo(a.getAmount()) > 0) {
                throw AppException.badRequest("Одобренная сумма не может превышать запрошенную");
            }
            if (a.getRecommendedLimit() != null && approved.compareTo(a.getRecommendedLimit()) > 0 && comment.isEmpty()) {
                throw AppException.badRequest("Сумма превышает рекомендованный лимит скоринга: укажите комментарий-обоснование");
            }
        } else if ("REJECTED".equals(decision)) {
            if (comment.isEmpty()) {
                throw AppException.badRequest("При отказе необходим комментарий");
            }
        } else {
            throw AppException.badRequest("Решение: APPROVED или REJECTED");
        }
        CreditDecision d = new CreditDecision();
        d.setId(UUID.randomUUID());
        d.setApplicationId(a.getId());
        d.setDecidedBy(actor.userUuid());
        d.setDecision(decision);
        d.setApprovedAmount(approved);
        d.setComment(comment.isEmpty() ? null : comment);
        d.setDecidedAt(OffsetDateTime.now());
        decisions.save(d);
        changeStatus(a, decision, actor, comment.isEmpty() ? "Кредитное решение принято" : comment);
        events.publish(Topics.APPLICATION_EVENTS, Topics.DECISION_MADE, a.getId().toString(), a.getCompanyId().toString(),
                Map.of("decision", decision, "approvedAmount", approved == null ? 0 : approved.doubleValue()));
        return Mapper.toProto(d);
    }

    // ---------- обработка событий ----------

    /** Результат скоринга из Kafka. Операция идемпотентна: повторная доставка даёт то же состояние. */
    @Transactional
    public void applyScoring(UUID applicationId, String rating, double score, double recommendedLimit) {
        CreditApplication a = applications.findById(applicationId).orElseThrow(() -> AppException.notFound("Заявка"));
        a.setLatestRating(rating);
        a.setLatestScore(BigDecimal.valueOf(score));
        a.setRecommendedLimit(BigDecimal.valueOf(recommendedLimit));
        log.info("BUSINESS application.scoringApplied id={} rating={} score={} limit={}", applicationId, rating, score, recommendedLimit);
    }

    // ---------- вспомогательное ----------

    private CreditApplication ownedByAnalyst(Actor actor, UUID id) {
        CreditApplication a = queries.load(actor, id);
        if (a.getAssignedAnalystId() == null || !a.getAssignedAnalystId().equals(actor.userUuid())) {
            throw AppException.forbidden("Операция доступна только аналитику, за которым закреплена заявка");
        }
        return a;
    }

    private static void requireStatus(CreditApplication a, String expected) {
        if (!expected.equals(a.getStatus())) {
            throw AppException.conflict("Операция недоступна: заявка в статусе %s, требуется %s".formatted(a.getStatus(), expected));
        }
    }

    private void changeStatus(CreditApplication a, String to, Actor actor, String comment) {
        String from = a.getStatus();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw AppException.conflict("Переход статуса %s -> %s недопустим".formatted(from, to));
        }
        a.setStatus(to);
        history.save(new StatusHistory(a.getId(), from, to, actor.userUuid(), comment));
        events.publish(Topics.APPLICATION_EVENTS, Topics.STATUS_CHANGED, a.getId().toString(), a.getCompanyId().toString(),
                Map.of("fromStatus", from, "toStatus", to, "comment", comment == null ? "" : comment));
        log.info("BUSINESS application.status id={} {} -> {} by={}", a.getId(), from, to, actor.email());
    }

    static Map<String, Object> analysisPayload(CreditApplication a) {
        return Map.of("companyId", a.getCompanyId().toString(), "companyName", a.getCompanyName(),
                "industryCode", a.getIndustryCode(), "amount", a.getAmount().doubleValue());
    }

    private static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw AppException.badRequest("Некорректный идентификатор: " + s);
        }
    }

    List<String> finalStatuses() {
        return List.of("APPROVED", "REJECTED", "CANCELLED");
    }
}
