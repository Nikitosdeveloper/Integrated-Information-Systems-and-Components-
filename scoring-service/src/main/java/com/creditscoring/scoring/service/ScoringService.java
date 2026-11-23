package com.creditscoring.scoring.service;

import com.creditscoring.common.cache.ProtoCache;
import com.creditscoring.common.clients.AnalysisClient;
import com.creditscoring.common.error.AppException;
import com.creditscoring.common.kafka.EventPublisher;
import com.creditscoring.common.kafka.Topics;
import com.creditscoring.common.security.Actor;
import com.creditscoring.common.security.Roles;
import com.creditscoring.contracts.Empty;
import com.creditscoring.contracts.FinancialAnalysis;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.ModelList;
import com.creditscoring.contracts.RatioValue;
import com.creditscoring.scoring.entity.FactorScore;
import com.creditscoring.scoring.entity.ModelFactor;
import com.creditscoring.scoring.entity.ModelRatingBand;
import com.creditscoring.scoring.entity.ScoringResult;
import com.creditscoring.scoring.repository.ScoringModelRepository;
import com.creditscoring.scoring.repository.ScoringResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ScoringService {
    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);
    static final String ACTIVE_MODEL_KEY = "scoring:active-model";

    private final ScoringModelRepository models;
    private final ScoringResultRepository results;
    private final AnalysisClient analysis;
    private final EventPublisher events;
    private final ProtoCache cache;

    public ScoringService(ScoringModelRepository models, ScoringResultRepository results, AnalysisClient analysis,
                          EventPublisher events, ProtoCache cache) {
        this.models = models;
        this.results = results;
        this.analysis = analysis;
        this.events = events;
        this.cache = cache;
    }

    // ---------- расчёт (запускается событием Kafka) ----------

    /**
     * Скоринг заявки: коэффициенты последнего отчётного года берутся у Analysis Service (gRPC),
     * применяется активная модель. Результат сохраняется и публикуется как SCORING_COMPLETED.
     */
    @Transactional
    public void score(UUID applicationId, double amount) {
        FinancialAnalysis a = analysis.getAnalysis(Actor.SYSTEM, IdRequest.newBuilder().setId(applicationId.toString()).build());
        int latestYear = a.getRatiosList().stream().mapToInt(RatioValue::getPeriodYear).max()
                .orElseThrow(() -> AppException.badRequest("В анализе заявки нет коэффициентов"));
        Map<String, Double> ratios = new HashMap<>();
        a.getRatiosList().stream().filter(r -> r.getPeriodYear() == latestYear).forEach(r -> ratios.put(r.getRatioCode(), r.getValue()));

        com.creditscoring.contracts.ScoringModel model = activeModel();
        ScoreCalculator.Result calc = ScoreCalculator.compute(model, ratios);
        double limit = Math.round(amount * calc.limitPercent() / 100.0 * 100) / 100.0;

        ScoringResult r = new ScoringResult();
        r.setId(UUID.randomUUID());
        r.setApplicationId(applicationId);
        r.setModelId(UUID.fromString(model.getId()));
        r.setTotalScore(BigDecimal.valueOf(calc.totalScore()));
        r.setRating(calc.rating());
        r.setRecommendedLimit(BigDecimal.valueOf(limit));
        r.setStopTriggered(calc.stopTriggered());
        r.setCalculatedAt(OffsetDateTime.now());
        calc.factors().forEach(f -> r.getFactorScores().add(new FactorScore(r, f.ratioCode(), BigDecimal.valueOf(f.ratioValue()),
                BigDecimal.valueOf(f.score()), BigDecimal.valueOf(f.weight()))));
        results.save(r);
        log.info("BUSINESS scoring.calculated application={} model=v{} score={} rating={} stop={} limit={}", applicationId,
                model.getVersion(), calc.totalScore(), calc.rating(), calc.stopTriggered(), limit);

        events.publish(Topics.SCORING_EVENTS, Topics.SCORING_COMPLETED, applicationId.toString(), null,
                Map.of("rating", calc.rating(), "totalScore", calc.totalScore(), "recommendedLimit", limit, "stopTriggered", calc.stopTriggered()));
    }

    /** Активная модель читается при каждом скоринге, а меняется редко: кеш Redis, сбрасывается при активации версии. */
    com.creditscoring.contracts.ScoringModel activeModel() {
        return cache.getOrLoad(ACTIVE_MODEL_KEY, com.creditscoring.contracts.ScoringModel.getDefaultInstance(), Duration.ofMinutes(30), () ->
                toProto(models.findFirstByStatus("ACTIVE").orElseThrow(() -> AppException.conflict("Нет активной скоринговой модели"))));
    }

    // ---------- чтение ----------

    @Transactional(readOnly = true)
    public com.creditscoring.contracts.ScoringResult getResult(Actor actor, UUID applicationId) {
        actor.requireRole(Roles.ANALYST, Roles.COMMITTEE, Roles.ADMIN, Roles.SYSTEM);
        ScoringResult r = results.findFirstByApplicationIdOrderByCalculatedAtDesc(applicationId)
                .orElseThrow(() -> AppException.notFound("Результат скоринга заявки"));
        Map<String, String> names = ratioNames(actor);
        var b = com.creditscoring.contracts.ScoringResult.newBuilder().setId(r.getId().toString()).setApplicationId(applicationId.toString())
                .setModelId(r.getModelId().toString()).setTotalScore(r.getTotalScore().doubleValue()).setRating(r.getRating())
                .setRecommendedLimit(r.getRecommendedLimit().doubleValue()).setStopTriggered(r.isStopTriggered())
                .setCalculatedAt(r.getCalculatedAt().toString());
        r.getFactorScores().forEach(f -> b.addFactorScores(com.creditscoring.contracts.FactorScore.newBuilder()
                .setRatioCode(f.getRatioCode()).setRatioName(names.getOrDefault(f.getRatioCode(), f.getRatioCode()))
                .setRatioValue(f.getRatioValue().doubleValue()).setScore(f.getScore().doubleValue()).setWeight(f.getWeight().doubleValue()).build()));
        return b.build();
    }

    @Transactional(readOnly = true)
    public ModelList listModels(Actor actor) {
        actor.requireRole(Roles.ADMIN, Roles.SYSTEM);
        return ModelList.newBuilder().addAllItems(models.findAllByOrderByVersionDesc().stream().map(m -> toProto(m)).toList()).build();
    }

    @Transactional(readOnly = true)
    public com.creditscoring.contracts.ScoringModel getModel(Actor actor, UUID id) {
        actor.requireRole(Roles.ADMIN, Roles.COMMITTEE, Roles.ANALYST, Roles.SYSTEM);
        return toProto(models.findById(id).orElseThrow(() -> AppException.notFound("Скоринговая модель")));
    }

    // ---------- бизнес-операции ----------

    /** Активация версии модели: сумма весов = 100, есть шкала рейтингов с нижней границей 0; прежняя активная архивируется. */
    @Transactional
    public com.creditscoring.contracts.ScoringModel activate(Actor actor, UUID id) {
        actor.requireRole(Roles.ADMIN);
        com.creditscoring.scoring.entity.ScoringModel m = models.findById(id).orElseThrow(() -> AppException.notFound("Скоринговая модель"));
        if ("ACTIVE".equals(m.getStatus())) {
            throw AppException.conflict("Модель уже активна");
        }
        BigDecimal sum = m.getFactors().stream().map(ModelFactor::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw AppException.conflict("Сумма весов факторов должна быть равна 100, сейчас " + sum);
        }
        if (m.getBands().stream().noneMatch(b -> b.getMinScore().signum() == 0)) {
            throw AppException.conflict("В шкале рейтингов нет диапазона с нижней границей 0");
        }
        models.findFirstByStatus("ACTIVE").ifPresent(old -> old.setStatus("ARCHIVED"));
        models.flush();
        m.setStatus("ACTIVE");
        models.flush();
        cache.evict(ACTIVE_MODEL_KEY);
        log.info("BUSINESS model.activate model={} v{} by={}", m.getName(), m.getVersion(), actor.email());
        return toProto(m);
    }

    // ---------- вспомогательное ----------

    private Map<String, String> ratioNames(Actor actor) {
        // названия коэффициентов хранит Analysis Service: запрос по gRPC (там он кешируется)
        return analysis.listRatioDefinitions(actor.hasRole(Roles.SYSTEM) ? actor : Actor.SYSTEM, Empty.getDefaultInstance()).getItemsList().stream()
                .collect(Collectors.toMap(com.creditscoring.contracts.RatioDefinition::getCode, com.creditscoring.contracts.RatioDefinition::getName));
    }

    private com.creditscoring.contracts.ScoringModel toProto(com.creditscoring.scoring.entity.ScoringModel m) {
        Map<String, String> names = ratioNames(Actor.SYSTEM);
        var b = com.creditscoring.contracts.ScoringModel.newBuilder().setId(m.getId().toString()).setName(m.getName())
                .setVersion(m.getVersion()).setStatus(m.getStatus()).setCreatedBy(m.getCreatedBy().toString()).setCreatedAt(m.getCreatedAt().toString());
        m.getFactors().stream().sorted(Comparator.comparing(ModelFactor::getId)).forEach(f -> {
            var fb = com.creditscoring.contracts.ModelFactor.newBuilder().setRatioCode(f.getRatioCode())
                    .setRatioName(names.getOrDefault(f.getRatioCode(), f.getRatioCode())).setWeight(f.getWeight().doubleValue())
                    .setIsStopFactor(f.isStopFactor()).setBadValue(f.getBadValue().doubleValue()).setGoodValue(f.getGoodValue().doubleValue());
            if (f.getStopValue() != null) {
                fb.setStopValue(f.getStopValue().doubleValue());
            }
            b.addFactors(fb);
        });
        m.getBands().stream().sorted(Comparator.comparing(ModelRatingBand::getMinScore).reversed()).forEach(band ->
                b.addBands(com.creditscoring.contracts.RatingBand.newBuilder().setRating(band.getRating())
                        .setMinScore(band.getMinScore().doubleValue()).setLimitPercent(band.getLimitPercent().doubleValue())));
        return b.build();
    }
}
