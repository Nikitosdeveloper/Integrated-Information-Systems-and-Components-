package com.creditscoring.analysis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Расчёт финансовых коэффициентов по строкам баланса и отчёта о прибылях (чистая функция, без обращений к БД). */
public final class RatioCalculator {

    /** @return коэффициент по коду; пусто, если знаменатель равен нулю (или капитал неположителен для долговой нагрузки) */
    public static Map<String, BigDecimal> calculate(Map<String, BigDecimal> balance, Map<String, BigDecimal> pl) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        put(out, "CURRENT_RATIO", div(balance.get("CURRENT_ASSETS"), balance.get("CURRENT_LIABILITIES")));
        put(out, "QUICK_RATIO", div(balance.get("CURRENT_ASSETS").subtract(balance.get("INVENTORY")), balance.get("CURRENT_LIABILITIES")));
        put(out, "CASH_RATIO", div(balance.get("CASH"), balance.get("CURRENT_LIABILITIES")));
        put(out, "EQUITY_RATIO", div(balance.get("EQUITY"), balance.get("TOTAL_ASSETS")));
        if (balance.get("EQUITY").signum() > 0) {
            put(out, "DEBT_TO_EQUITY", div(balance.get("CURRENT_LIABILITIES").add(balance.get("LONGTERM_LIABILITIES")), balance.get("EQUITY")));
        }
        put(out, "ROA", div(pl.get("NET_PROFIT"), balance.get("TOTAL_ASSETS")));
        put(out, "NET_MARGIN", div(pl.get("NET_PROFIT"), pl.get("REVENUE")));
        return out;
    }

    /** Оценка значения относительно нормативов: GOOD / NORMAL / BAD. */
    public static String assess(BigDecimal value, BigDecimal good, BigDecimal ok, boolean higherIsBetter) {
        int cmpGood = value.compareTo(good);
        int cmpOk = value.compareTo(ok);
        if (higherIsBetter) {
            return cmpGood >= 0 ? "GOOD" : cmpOk >= 0 ? "NORMAL" : "BAD";
        }
        return cmpGood <= 0 ? "GOOD" : cmpOk <= 0 ? "NORMAL" : "BAD";
    }

    private static void put(Map<String, BigDecimal> map, String code, Optional<BigDecimal> value) {
        value.ifPresent(v -> map.put(code, v));
    }

    private static Optional<BigDecimal> div(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || b.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(a.divide(b, 4, RoundingMode.HALF_UP));
    }

    private RatioCalculator() {
    }
}
