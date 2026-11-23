package com.creditscoring.analysis.service;

import com.creditscoring.analysis.entity.BenchmarkSet;
import com.creditscoring.analysis.entity.FinancialAnalysis;
import com.creditscoring.analysis.entity.IndustryBenchmark;
import com.creditscoring.analysis.entity.RatioDefinition;
import com.creditscoring.analysis.entity.RatioValue;
import com.creditscoring.analysis.repository.BenchmarkSetRepository;
import com.creditscoring.analysis.repository.FinancialAnalysisRepository;
import com.creditscoring.analysis.repository.RatioDefinitionRepository;
import com.creditscoring.common.cache.ProtoCache;
import com.creditscoring.common.clients.BorrowerClient;
import com.creditscoring.common.clients.StatementClient;
import com.creditscoring.common.error.AppException;
import com.creditscoring.common.kafka.EventPublisher;
import com.creditscoring.common.kafka.Topics;
import com.creditscoring.common.security.Actor;
import com.creditscoring.common.security.Roles;
import com.creditscoring.contracts.BenchmarkSetList;
import com.creditscoring.contracts.Empty;
import com.creditscoring.contracts.ListCompanyStatementsRequest;
import com.creditscoring.contracts.RatioDefinitionList;
import com.creditscoring.contracts.StatementLine;
import com.creditscoring.contracts.StatementList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnalysisService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    static final String RATIOS_KEY = "analysis:ratio-definitions";

    private final FinancialAnalysisRepository analyses;
    private final RatioDefinitionRepository definitions;
    private final BenchmarkSetRepository benchmarkSets;
    private final StatementClient statements;
    private final BorrowerClient borrowers;
    private final EventPublisher events;
    private final ProtoCache cache;

    public AnalysisService(FinancialAnalysisRepository analyses, RatioDefinitionRepository definitions,
                           BenchmarkSetRepository benchmarkSets, StatementClient statements, BorrowerClient borrowers,
                           EventPublisher events, ProtoCache cache) {
        this.analyses = analyses;
        this.definitions = definitions;
        this.benchmarkSets = benchmarkSets;
        this.statements = statements;
        this.borrowers = borrowers;
        this.events = events;
        this.cache = cache;
    }

    // ---------- расчёт (запускается событием Kafka) ----------

    /**
     * Рассчитывает коэффициенты компании за последние два отчётных года с полным комплектом форм
     * (данные получает у Statement Service по gRPC), оценивает их по активным нормативам отрасли
     * и публикует ANALYSIS_COMPLETED. Повторный расчёт заменяет предыдущий результат (операция идемпотентна).
     */
    @Transactional
    public void analyze(UUID applicationId, UUID companyId, String industryCode, double amount) {
        StatementList list = statements.listCompanyStatements(Actor.SYSTEM, ListCompanyStatementsRequest.newBuilder()
                .setCompanyId(companyId.toString()).setIncludeLines(true).setOnlyValid(true).build());

        Map<Integer, Map<String, Map<String, BigDecimal>>> byYear = new TreeMap<>();
        list.getItemsList().forEach(s -> byYear.computeIfAbsent(s.getPeriodYear(), y -> new HashMap<>())
                .put(s.getFormType(), s.getLinesList().stream()
                        .collect(Collectors.toMap(StatementLine::getCode, l -> BigDecimal.valueOf(l.getValue())))));
        List<Integer> years = byYear.entrySet().stream()
                .filter(e -> e.getValue().containsKey("BALANCE") && e.getValue().containsKey("PROFIT_LOSS"))
                .map(Map.Entry::getKey).sorted(java.util.Comparator.reverseOrder()).limit(2).toList();
        if (years.isEmpty()) {
            throw AppException.badRequest("У компании " + companyId + " нет валидного комплекта отчётности (баланс и отчёт о прибылях за один год)");
        }

        BenchmarkSet set = benchmarkSets.findFirstByStatus("ACTIVE").orElseThrow(() -> AppException.conflict("Нет активного набора нормативов"));
        Map<String, RatioDefinition> defs = definitions.findAll().stream().collect(Collectors.toMap(RatioDefinition::getCode, Function.identity()));

        analyses.findByApplicationId(applicationId).ifPresent(old -> {
            analyses.delete(old);
            analyses.flush();
        });
        FinancialAnalysis a = new FinancialAnalysis();
        a.setId(UUID.randomUUID());
        a.setApplicationId(applicationId);
        a.setCompanyId(companyId);
        a.setBenchmarkSetId(set.getId());
        a.setCalculatedAt(OffsetDateTime.now());
        for (int year : years) {
            Map<String, BigDecimal> ratios = RatioCalculator.calculate(byYear.get(year).get("BALANCE"), byYear.get(year).get("PROFIT_LOSS"));
            ratios.forEach((code, value) -> {
                IndustryBenchmark bm = benchmark(set, industryCode, code);
                String assessment = bm == null ? null : RatioCalculator.assess(value, bm.getGoodThreshold(), bm.getOkThreshold(), defs.get(code).isHigherIsBetter());
                a.getValues().add(new RatioValue(a, code, year, value, assessment));
            });
        }
        analyses.save(a);
        log.info("BUSINESS analysis.calculated application={} company={} years={} ratios={} benchmarkSet={}", applicationId, companyId, years, a.getValues().size(), set.getName());

        events.publish(Topics.ANALYSIS_EVENTS, Topics.ANALYSIS_COMPLETED, applicationId.toString(), companyId.toString(),
                Map.of("companyId", companyId.toString(), "industryCode", industryCode, "amount", amount));
    }

    private static IndustryBenchmark benchmark(BenchmarkSet set, String industry, String ratio) {
        IndustryBenchmark fallback = null;
        for (IndustryBenchmark b : set.getItems()) {
            if (!b.getRatioCode().equals(ratio)) {
                continue;
            }
            if (b.getIndustryCode().equals(industry)) {
                return b;
            }
            if (b.getIndustryCode().equals("ALL")) {
                fallback = b;
            }
        }
        return fallback;
    }

    // ---------- чтение ----------

    @Transactional(readOnly = true)
    public com.creditscoring.contracts.FinancialAnalysis get(Actor actor, UUID applicationId) {
        actor.requireRole(Roles.ANALYST, Roles.COMMITTEE, Roles.ADMIN, Roles.SYSTEM);
        FinancialAnalysis a = analyses.findByApplicationId(applicationId).orElseThrow(() -> AppException.notFound("Финансовый анализ заявки"));
        Map<String, com.creditscoring.contracts.RatioDefinition> defs = ratioDefinitions().getItemsList().stream()
                .collect(Collectors.toMap(com.creditscoring.contracts.RatioDefinition::getCode, Function.identity()));
        String setName = benchmarkSets.findById(a.getBenchmarkSetId()).map(BenchmarkSet::getName).orElse("");
        var b = com.creditscoring.contracts.FinancialAnalysis.newBuilder()
                .setId(a.getId().toString()).setApplicationId(applicationId.toString()).setCompanyId(a.getCompanyId().toString())
                .setBenchmarkSetId(a.getBenchmarkSetId().toString()).setBenchmarkSetName(setName).setCalculatedAt(a.getCalculatedAt().toString());
        a.getValues().stream()
                .sorted(java.util.Comparator.comparingInt(RatioValue::getPeriodYear).reversed().thenComparing(RatioValue::getRatioCode))
                .forEach(v -> b.addRatios(com.creditscoring.contracts.RatioValue.newBuilder()
                        .setRatioCode(v.getRatioCode()).setDefinition(defs.get(v.getRatioCode())).setPeriodYear(v.getPeriodYear())
                        .setValue(v.getValue().doubleValue()).setAssessment(v.getAssessment() == null ? "" : v.getAssessment()).build()));
        return b.build();
    }

    /** Справочник коэффициентов читается очень часто (каждый скоринг, каждая карточка) и почти не меняется: кеш. */
    public RatioDefinitionList ratioDefinitions() {
        return cache.getOrLoad(RATIOS_KEY, RatioDefinitionList.getDefaultInstance(), Duration.ofHours(1), () ->
                RatioDefinitionList.newBuilder().addAllItems(definitions.findAll().stream().map(d ->
                        com.creditscoring.contracts.RatioDefinition.newBuilder().setCode(d.getCode()).setName(d.getName())
                                .setFormula(d.getFormula()).setHigherIsBetter(d.isHigherIsBetter()).build()).toList()).build());
    }

    @Transactional(readOnly = true)
    public BenchmarkSetList listSets(Actor actor) {
        actor.requireRole(Roles.ADMIN);
        return BenchmarkSetList.newBuilder().addAllItems(benchmarkSets.findAllByOrderByCreatedAtDesc().stream()
                .map(s -> toProto(actor, s, false)).toList()).build();
    }

    @Transactional(readOnly = true)
    public com.creditscoring.contracts.BenchmarkSet getSet(Actor actor, UUID id) {
        actor.requireRole(Roles.ADMIN);
        return toProto(actor, benchmarkSets.findById(id).orElseThrow(() -> AppException.notFound("Набор нормативов")), true);
    }

    // ---------- бизнес-операции ----------

    /** Активация набора нормативов: прежний активный архивируется. Набор должен покрывать все коэффициенты значениями по умолчанию. */
    @Transactional
    public com.creditscoring.contracts.BenchmarkSet activate(Actor actor, UUID id) {
        actor.requireRole(Roles.ADMIN);
        BenchmarkSet set = benchmarkSets.findById(id).orElseThrow(() -> AppException.notFound("Набор нормативов"));
        if ("ACTIVE".equals(set.getStatus())) {
            throw AppException.conflict("Набор уже активен");
        }
        long required = definitions.count();
        long covered = set.getItems().stream().filter(i -> i.getIndustryCode().equals("ALL")).count();
        if (covered < required) {
            throw AppException.conflict("Набор не покрывает все коэффициенты значениями по умолчанию (ALL): " + covered + " из " + required);
        }
        benchmarkSets.findFirstByStatus("ACTIVE").ifPresent(old -> old.setStatus("ARCHIVED"));
        benchmarkSets.flush();
        set.setStatus("ACTIVE");
        benchmarkSets.flush();
        log.info("BUSINESS benchmarks.activate set={} by={}", set.getName(), actor.email());
        return toProto(actor, set, true);
    }

    private com.creditscoring.contracts.BenchmarkSet toProto(Actor actor, BenchmarkSet s, boolean withItems) {
        var b = com.creditscoring.contracts.BenchmarkSet.newBuilder().setId(s.getId().toString()).setName(s.getName())
                .setStatus(s.getStatus()).setCreatedAt(s.getCreatedAt().toString());
        if (withItems) {
            // названия отраслей хранятся в Borrower Service (здесь только код) - запрос по gRPC
            Map<String, String> industries = borrowers.listIndustries(actor, Empty.getDefaultInstance()).getItemsList().stream()
                    .collect(Collectors.toMap(com.creditscoring.contracts.Industry::getCode, com.creditscoring.contracts.Industry::getName));
            Map<String, String> ratioNames = ratioDefinitions().getItemsList().stream()
                    .collect(Collectors.toMap(com.creditscoring.contracts.RatioDefinition::getCode, com.creditscoring.contracts.RatioDefinition::getName));
            s.getItems().stream().sorted(java.util.Comparator.comparing(IndustryBenchmark::getIndustryCode).thenComparing(IndustryBenchmark::getRatioCode))
                    .forEach(i -> b.addItems(com.creditscoring.contracts.BenchmarkItem.newBuilder()
                            .setIndustryCode(i.getIndustryCode()).setIndustryName(industries.getOrDefault(i.getIndustryCode(), "Прочие отрасли"))
                            .setRatioCode(i.getRatioCode()).setRatioName(ratioNames.getOrDefault(i.getRatioCode(), i.getRatioCode()))
                            .setGoodThreshold(i.getGoodThreshold().doubleValue()).setOkThreshold(i.getOkThreshold().doubleValue()).build()));
        }
        return b.build();
    }
}
