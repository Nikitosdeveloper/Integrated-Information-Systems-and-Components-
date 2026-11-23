package com.creditscoring.statement.service;

import com.creditscoring.common.error.AppException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Контроль формы отчётности: состав строк и арифметические соотношения. */
final class StatementValidator {
    static final Set<String> BALANCE_CODES = Set.of("CASH", "INVENTORY", "CURRENT_ASSETS", "NONCURRENT_ASSETS", "TOTAL_ASSETS",
            "EQUITY", "LONGTERM_LIABILITIES", "CURRENT_LIABILITIES", "TOTAL_LIABILITIES_EQUITY");
    static final Set<String> PL_CODES = Set.of("REVENUE", "COST_OF_SALES", "NET_PROFIT");
    private static final BigDecimal TOLERANCE = new BigDecimal("1.00");

    record Issue(String severity, String message) {
    }

    /** Структурные ошибки (неизвестные или повторяющиеся коды, пропущенные строки) отклоняют загрузку целиком. */
    static void checkStructure(String formType, Map<String, BigDecimal> values, List<String> codesInOrder) {
        Set<String> allowed = "BALANCE".equals(formType) ? BALANCE_CODES : PL_CODES;
        if (codesInOrder.size() != values.size()) {
            throw AppException.badRequest("В форме есть повторяющиеся коды строк");
        }
        for (String code : values.keySet()) {
            if (!allowed.contains(code)) {
                throw AppException.badRequest("Неизвестный код строки для формы " + formType + ": " + code);
            }
        }
        for (String code : allowed) {
            if (!values.containsKey(code)) {
                throw AppException.badRequest("Не заполнена обязательная строка " + code);
            }
        }
    }

    static List<Issue> validate(String formType, Map<String, BigDecimal> v) {
        List<Issue> issues = new ArrayList<>();
        if ("BALANCE".equals(formType)) {
            for (Map.Entry<String, BigDecimal> e : v.entrySet()) {
                if (e.getValue().signum() < 0) {
                    issues.add(new Issue("ERROR", "Отрицательное значение строки " + e.getKey()));
                }
            }
            if (diff(v.get("CURRENT_ASSETS").add(v.get("NONCURRENT_ASSETS")), v.get("TOTAL_ASSETS"))) {
                issues.add(new Issue("ERROR", "Краткосрочные и долгосрочные активы не равны итогу актива"));
            }
            if (diff(v.get("EQUITY").add(v.get("LONGTERM_LIABILITIES")).add(v.get("CURRENT_LIABILITIES")), v.get("TOTAL_LIABILITIES_EQUITY"))) {
                issues.add(new Issue("ERROR", "Капитал и обязательства не равны итогу пассива"));
            }
            if (diff(v.get("TOTAL_ASSETS"), v.get("TOTAL_LIABILITIES_EQUITY"))) {
                issues.add(new Issue("ERROR", "Итог актива не равен итогу пассива баланса"));
            }
            if (v.get("CASH").add(v.get("INVENTORY")).compareTo(v.get("CURRENT_ASSETS")) > 0) {
                issues.add(new Issue("ERROR", "Денежные средства и запасы превышают краткосрочные активы"));
            }
        } else {
            if (v.get("REVENUE").signum() <= 0) {
                issues.add(new Issue("ERROR", "Выручка должна быть положительной"));
            }
            if (v.get("COST_OF_SALES").signum() < 0) {
                issues.add(new Issue("ERROR", "Себестоимость не может быть отрицательной"));
            }
            if (v.get("NET_PROFIT").compareTo(v.get("REVENUE").subtract(v.get("COST_OF_SALES")).add(TOLERANCE)) > 0) {
                issues.add(new Issue("WARNING", "Чистая прибыль превышает валовую прибыль"));
            }
        }
        return issues;
    }

    private static boolean diff(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCE) > 0;
    }

    private StatementValidator() {
    }
}
