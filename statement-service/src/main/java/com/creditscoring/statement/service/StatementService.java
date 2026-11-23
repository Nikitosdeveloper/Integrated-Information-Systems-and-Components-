package com.creditscoring.statement.service;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.security.Actor;
import com.creditscoring.common.security.Roles;
import com.creditscoring.contracts.IssueList;
import com.creditscoring.contracts.ListCompanyStatementsRequest;
import com.creditscoring.contracts.StatementLineList;
import com.creditscoring.contracts.StatementList;
import com.creditscoring.contracts.UploadStatementRequest;
import com.creditscoring.statement.entity.FinancialStatement;
import com.creditscoring.statement.entity.StatementLine;
import com.creditscoring.statement.entity.ValidationIssue;
import com.creditscoring.statement.repository.FinancialStatementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StatementService {
    private static final Logger log = LoggerFactory.getLogger(StatementService.class);

    private final FinancialStatementRepository statements;

    public StatementService(FinancialStatementRepository statements) {
        this.statements = statements;
    }

    @Transactional(readOnly = true)
    public com.creditscoring.contracts.FinancialStatement get(Actor actor, UUID id) {
        FinancialStatement s = statements.findById(id).orElseThrow(() -> AppException.notFound("Отчётность"));
        checkRead(actor, s.getCompanyId());
        return Mapper.toProto(s, true);
    }

    @Transactional(readOnly = true)
    public StatementList getMany(Actor actor, List<UUID> ids) {
        List<FinancialStatement> found = statements.findByIdIn(ids);
        found.forEach(s -> checkRead(actor, s.getCompanyId()));
        return StatementList.newBuilder().addAllItems(found.stream().map(s -> Mapper.toProto(s, false)).toList()).build();
    }

    @Transactional(readOnly = true)
    public StatementList listForCompany(Actor actor, ListCompanyStatementsRequest r) {
        UUID companyId = UUID.fromString(r.getCompanyId());
        checkRead(actor, companyId);
        List<FinancialStatement> list = r.getOnlyValid()
                ? statements.findByCompanyIdAndStatusOrderByPeriodYearDescFormTypeAsc(companyId, "VALID")
                : statements.findByCompanyIdOrderByPeriodYearDescFormTypeAsc(companyId);
        return StatementList.newBuilder().addAllItems(list.stream().map(s -> Mapper.toProto(s, r.getIncludeLines())).toList()).build();
    }

    @Transactional(readOnly = true)
    public StatementLineList lines(Actor actor, UUID id) {
        FinancialStatement s = statements.findById(id).orElseThrow(() -> AppException.notFound("Отчётность"));
        checkRead(actor, s.getCompanyId());
        return StatementLineList.newBuilder().addAllItems(s.getLines().stream().map(Mapper::toProto).toList()).build();
    }

    @Transactional(readOnly = true)
    public IssueList issues(Actor actor, UUID id) {
        FinancialStatement s = statements.findById(id).orElseThrow(() -> AppException.notFound("Отчётность"));
        checkRead(actor, s.getCompanyId());
        return IssueList.newBuilder().addAllItems(s.getIssues().stream().map(Mapper::toProto).toList()).build();
    }

    /**
     * Бизнес-операция: загрузка формы отчётности заёмщиком.
     * Проверки: роль, принадлежность компании, допустимый период, уникальность формы за год,
     * состав строк, арифметический контроль баланса. Форма с ошибками контроля сохраняется как INVALID
     * (заёмщик видит замечания), но к анализу не допускается.
     */
    @Transactional
    public com.creditscoring.contracts.FinancialStatement upload(Actor actor, UploadStatementRequest r) {
        actor.requireRole(Roles.BORROWER);
        UUID companyId = actor.companyUuid();
        if (!r.getCompanyId().isBlank() && !r.getCompanyId().equals(actor.companyId())) {
            throw AppException.forbidden("Нельзя загружать отчётность за другую компанию");
        }
        String form = r.getFormType();
        if (!"BALANCE".equals(form) && !"PROFIT_LOSS".equals(form)) {
            throw AppException.badRequest("formType должен быть BALANCE или PROFIT_LOSS");
        }
        int maxYear = OffsetDateTime.now().getYear();
        if (r.getPeriodYear() < 2000 || r.getPeriodYear() > maxYear) {
            throw AppException.badRequest("Отчётный год должен быть от 2000 до " + maxYear);
        }
        if (r.getLinesCount() == 0) {
            throw AppException.badRequest("Форма не содержит строк");
        }
        if (statements.existsByCompanyIdAndFormTypeAndPeriodYear(companyId, form, r.getPeriodYear())) {
            throw AppException.conflict("Форма " + form + " за " + r.getPeriodYear() + " год уже загружена");
        }
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        List<String> codes = new ArrayList<>();
        r.getLinesList().forEach(l -> {
            values.put(l.getCode(), BigDecimal.valueOf(l.getValue()));
            codes.add(l.getCode());
        });
        StatementValidator.checkStructure(form, values, codes);
        List<StatementValidator.Issue> found = StatementValidator.validate(form, values);

        FinancialStatement s = new FinancialStatement();
        s.setId(UUID.randomUUID());
        s.setCompanyId(companyId);
        s.setFormType(form);
        s.setPeriodYear(r.getPeriodYear());
        s.setCreatedBy(actor.userUuid());
        s.setCreatedAt(OffsetDateTime.now());
        boolean hasErrors = found.stream().anyMatch(i -> "ERROR".equals(i.severity()));
        s.setStatus(hasErrors ? "INVALID" : "VALID");
        r.getLinesList().forEach(l -> s.getLines().add(new StatementLine(s, l.getCode(), l.getName().isBlank() ? l.getCode() : l.getName(), BigDecimal.valueOf(l.getValue()))));
        found.forEach(i -> s.getIssues().add(new ValidationIssue(s, i.severity(), i.message())));
        statements.save(s);
        log.info("BUSINESS statement.upload company={} form={} year={} status={} issues={}", companyId, form, r.getPeriodYear(), s.getStatus(), found.size());
        return Mapper.toProto(s, true);
    }

    private static void checkRead(Actor actor, UUID companyId) {
        if (actor.isBorrower() && !companyId.toString().equals(actor.companyId())) {
            throw AppException.forbidden("Доступ к отчётности другой компании запрещён");
        }
    }

    static final class Mapper {
        static com.creditscoring.contracts.StatementLine toProto(StatementLine l) {
            return com.creditscoring.contracts.StatementLine.newBuilder().setCode(l.getCode()).setName(l.getName())
                    .setValue(l.getValue().doubleValue()).build();
        }

        static com.creditscoring.contracts.ValidationIssue toProto(ValidationIssue i) {
            return com.creditscoring.contracts.ValidationIssue.newBuilder().setSeverity(i.getSeverity()).setMessage(i.getMessage()).build();
        }

        static com.creditscoring.contracts.FinancialStatement toProto(FinancialStatement s, boolean withDetails) {
            var b = com.creditscoring.contracts.FinancialStatement.newBuilder()
                    .setId(s.getId().toString()).setCompanyId(s.getCompanyId().toString()).setFormType(s.getFormType())
                    .setPeriodYear(s.getPeriodYear()).setStatus(s.getStatus()).setCreatedAt(s.getCreatedAt().toString());
            if (withDetails) {
                s.getLines().forEach(l -> b.addLines(toProto(l)));
                s.getIssues().forEach(i -> b.addIssues(toProto(i)));
            }
            return b.build();
        }
    }
}
