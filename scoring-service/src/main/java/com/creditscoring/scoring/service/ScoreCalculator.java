package com.creditscoring.scoring.service;

import com.creditscoring.contracts.ModelFactor;
import com.creditscoring.contracts.RatingBand;
import com.creditscoring.contracts.ScoringModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Расчёт балла по модели: каждый фактор оценивается линейно от 0 (значение "плохо") до 100 (значение "хорошо"),
 * итог - взвешенная сумма. Если не выполнено условие стоп-фактора, рейтинг принудительно минимальный.
 */
public final class ScoreCalculator {

    public record FactorResult(String ratioCode, double ratioValue, double score, double weight) {
    }

    public record Result(double totalScore, String rating, double limitPercent, boolean stopTriggered, List<FactorResult> factors) {
    }

    public static Result compute(ScoringModel model, Map<String, Double> ratios) {
        double weighted = 0;
        double weights = 0;
        boolean stop = false;
        List<FactorResult> out = new ArrayList<>();
        for (ModelFactor f : model.getFactorsList()) {
            Double v = ratios.get(f.getRatioCode());
            double score = v == null ? 0 : factorScore(v, f.getBadValue(), f.getGoodValue());
            if (f.getIsStopFactor() && (v == null || (f.hasStopValue() && v < f.getStopValue()))) {
                stop = true;
            }
            weighted += f.getWeight() * score;
            weights += f.getWeight();
            out.add(new FactorResult(f.getRatioCode(), v == null ? 0 : v, round1(score), f.getWeight()));
        }
        double total = weights == 0 ? 0 : round1(weighted / weights);
        List<RatingBand> bands = model.getBandsList().stream().sorted(Comparator.comparingDouble(RatingBand::getMinScore).reversed()).toList();
        RatingBand lowest = bands.get(bands.size() - 1);
        RatingBand band = stop ? lowest : bands.stream().filter(b -> total >= b.getMinScore()).findFirst().orElse(lowest);
        return new Result(total, band.getRating(), stop ? 0 : band.getLimitPercent(), stop, out);
    }

    static double factorScore(double value, double bad, double good) {
        double ratio = (value - bad) / (good - bad);
        return Math.max(0, Math.min(1, ratio)) * 100;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private ScoreCalculator() {
    }
}
