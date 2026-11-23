#!/usr/bin/env python3
"""Генерирует 02_seed.sql для statement_db, analysis_db и scoring_db (согласованные тестовые данные)."""
import os

BASE = os.path.dirname(os.path.abspath(__file__)) + "/postgres"
ADMIN = "a0000000-0000-4000-8000-000000000001"
COMPANIES = {1: "c0000000-0000-4000-8000-000000000001", 2: "c0000000-0000-4000-8000-000000000002", 3: "c0000000-0000-4000-8000-000000000003"}
BORROWER_USER = {1: "a0000000-0000-4000-8000-000000000005", 2: "a0000000-0000-4000-8000-000000000006", 3: "a0000000-0000-4000-8000-000000000007"}

# (cash, inventory, other_current, noncurrent, equity, long_term, revenue, cost, net_profit); тыс. BYN
DATA = {
    (1, 2023): (120, 800, 380, 700, 900, 300, 5200, 4300, 180),
    (1, 2024): (150, 850, 450, 750, 1050, 300, 5800, 4750, 240),
    (2, 2023): (40, 1500, 700, 2000, 900, 1500, 6000, 5500, 60),
    (2, 2024): (30, 1700, 800, 2100, 850, 1700, 5500, 5300, -50),
    (3, 2023): (600, 20, 480, 400, 1100, 100, 4000, 2800, 700),
    (3, 2024): (800, 30, 570, 500, 1500, 100, 5200, 3600, 950),
}

def balance_lines(cash, inv, other, nca, eq, ltl, broken=False):
    ca = cash + inv + other
    ta = ca + nca
    cl = ta - eq - ltl
    tle = eq + ltl + cl + (150 if broken else 0)
    return [("CASH", "Денежные средства", cash), ("INVENTORY", "Запасы", inv), ("CURRENT_ASSETS", "Краткосрочные активы", ca),
            ("NONCURRENT_ASSETS", "Долгосрочные активы", nca), ("TOTAL_ASSETS", "Баланс (актив)", ta),
            ("EQUITY", "Собственный капитал", eq), ("LONGTERM_LIABILITIES", "Долгосрочные обязательства", ltl),
            ("CURRENT_LIABILITIES", "Краткосрочные обязательства", cl), ("TOTAL_LIABILITIES_EQUITY", "Баланс (пассив)", tle)]

def pl_lines(rev, cost, np):
    return [("REVENUE", "Выручка", rev), ("COST_OF_SALES", "Себестоимость продаж", cost), ("NET_PROFIT", "Чистая прибыль", np)]

out = ["-- Сгенерировано gen_seed.py"]
n = 0
issues = []
def add_statement(company, form, year, lines, status, issue_msgs=()):
    global n
    n += 1
    sid = "d0000000-0000-4000-8000-%012d" % n
    out.append("INSERT INTO financial_statements (id, company_id, form_type, period_year, status, created_by, created_at) VALUES "
               "('%s', '%s', '%s', %d, '%s', '%s', '2026-09-01 08:00+00');" % (sid, COMPANIES[company], form, year, status, BORROWER_USER[company]))
    for code, name, val in lines:
        out.append("INSERT INTO statement_lines (statement_id, code, name, value) VALUES ('%s', '%s', '%s', %s);" % (sid, code, name, val * 1000))
    for sev, msg in issue_msgs:
        out.append("INSERT INTO validation_issues (statement_id, severity, message) VALUES ('%s', '%s', '%s');" % (sid, sev, msg))

for (c, y), (cash, inv, oth, nca, eq, ltl, rev, cost, np) in DATA.items():
    add_statement(c, "BALANCE", y, balance_lines(cash, inv, oth, nca, eq, ltl), "VALID")
    add_statement(c, "PROFIT_LOSS", y, pl_lines(rev, cost, np), "VALID")
add_statement(1, "BALANCE", 2022, balance_lines(100, 700, 300, 650, 800, 300, broken=True), "INVALID",
              [("ERROR", "Итог актива не равен итогу пассива баланса")])
open(BASE + "/statement_db/02_seed.sql", "w", encoding="utf-8").write("\n".join(out) + "\n")

# ---- analysis_db
RATIOS = [("CURRENT_RATIO", "Коэффициент текущей ликвидности", "Краткосрочные активы / Краткосрочные обязательства", True),
          ("QUICK_RATIO", "Коэффициент быстрой ликвидности", "(Краткосрочные активы - Запасы) / Краткосрочные обязательства", True),
          ("CASH_RATIO", "Коэффициент абсолютной ликвидности", "Денежные средства / Краткосрочные обязательства", True),
          ("EQUITY_RATIO", "Коэффициент автономии", "Собственный капитал / Баланс", True),
          ("DEBT_TO_EQUITY", "Отношение долга к капиталу", "(Краткосрочные + долгосрочные обязательства) / Собственный капитал", False),
          ("ROA", "Рентабельность активов", "Чистая прибыль / Баланс", True),
          ("NET_MARGIN", "Рентабельность продаж", "Чистая прибыль / Выручка", True)]
BENCH = {  # ratio: {industry: (good, ok)}
    "CURRENT_RATIO": {"TRADE": (1.5, 1.1), "CONSTRUCTION": (1.3, 1.0), "IT": (1.8, 1.2), "ALL": (1.5, 1.1)},
    "QUICK_RATIO": {"TRADE": (0.8, 0.5), "CONSTRUCTION": (0.7, 0.4), "IT": (1.5, 1.0), "ALL": (0.8, 0.5)},
    "CASH_RATIO": {"TRADE": (0.2, 0.1), "CONSTRUCTION": (0.1, 0.05), "IT": (0.5, 0.25), "ALL": (0.2, 0.1)},
    "EQUITY_RATIO": {"TRADE": (0.4, 0.25), "CONSTRUCTION": (0.3, 0.2), "IT": (0.5, 0.3), "ALL": (0.4, 0.25)},
    "DEBT_TO_EQUITY": {"TRADE": (1.0, 2.0), "CONSTRUCTION": (1.5, 3.0), "IT": (0.7, 1.5), "ALL": (1.0, 2.0)},
    "ROA": {"TRADE": (0.08, 0.03), "CONSTRUCTION": (0.06, 0.02), "IT": (0.2, 0.08), "ALL": (0.08, 0.03)},
    "NET_MARGIN": {"TRADE": (0.05, 0.02), "CONSTRUCTION": (0.05, 0.02), "IT": (0.15, 0.07), "ALL": (0.05, 0.02)},
}
a = ["-- Сгенерировано gen_seed.py"]
for code, name, formula, hib in RATIOS:
    a.append("INSERT INTO ratio_definitions (code, name, formula, higher_is_better) VALUES ('%s', '%s', '%s', %s);" % (code, name, formula, str(hib).lower()))
a.append("INSERT INTO benchmark_sets (id, name, status, created_at) VALUES "
         "('f0000000-0000-4000-8000-000000000001', 'Отраслевые нормативы 2026', 'ACTIVE', '2026-01-10 09:00+00'), "
         "('f0000000-0000-4000-8000-000000000002', 'Отраслевые нормативы 2027 (черновик)', 'DRAFT', '2026-10-15 09:00+00');")
for code, _, _, _ in RATIOS:
    for ind, (g, o) in BENCH[code].items():
        a.append("INSERT INTO industry_benchmarks (set_id, industry_code, ratio_code, good_threshold, ok_threshold) VALUES "
                 "('f0000000-0000-4000-8000-000000000001', '%s', '%s', %s, %s);" % (ind, code, g, o))
        # черновик: ужесточённые нормативы
        a.append("INSERT INTO industry_benchmarks (set_id, industry_code, ratio_code, good_threshold, ok_threshold) VALUES "
                 "('f0000000-0000-4000-8000-000000000002', '%s', '%s', %s, %s);" % (ind, code, round(g * (1.1 if code != "DEBT_TO_EQUITY" else 0.9), 4), round(o * (1.1 if code != "DEBT_TO_EQUITY" else 0.9), 4)))
open(BASE + "/analysis_db/02_seed.sql", "w", encoding="utf-8").write("\n".join(a) + "\n")

# ---- scoring_db
s = ["-- Сгенерировано gen_seed.py"]
M = {1: ("e0000000-0000-4000-8000-000000000001", 1, "ARCHIVED"), 2: ("e0000000-0000-4000-8000-000000000002", 2, "ACTIVE"), 3: ("e0000000-0000-4000-8000-000000000003", 3, "DRAFT")}
for k, (mid, ver, st) in M.items():
    s.append("INSERT INTO scoring_models (id, name, version, status, created_by, created_at) VALUES ('%s', 'Базовая модель оценки', %d, '%s', '%s', '2026-0%d-01 09:00+00');" % (mid, ver, st, ADMIN, 6 + k))
# ratio: (bad, good)
RANGE = {"CURRENT_RATIO": (0.8, 2.0), "QUICK_RATIO": (0.3, 1.2), "CASH_RATIO": (0.02, 0.3), "EQUITY_RATIO": (0.1, 0.5),
         "DEBT_TO_EQUITY": (4.0, 0.5), "ROA": (0.0, 0.15), "NET_MARGIN": (0.0, 0.12)}
WEIGHTS = {1: [20, 15, 5, 20, 15, 10, 15], 2: [15, 10, 5, 20, 15, 15, 20], 3: [10, 10, 5, 25, 15, 15, 20]}
for k, (mid, ver, st) in M.items():
    for (code, _, _, _), w in zip(RATIOS, WEIGHTS[k]):
        bad, good = RANGE[code]
        stop = "0.1" if code == "EQUITY_RATIO" else "NULL"
        s.append("INSERT INTO model_factors (model_id, ratio_code, weight, is_stop_factor, bad_value, good_value, stop_value) VALUES ('%s', '%s', %s, %s, %s, %s, %s);"
                 % (mid, code, w, "true" if code == "EQUITY_RATIO" else "false", bad, good, stop))
    for rating, ms, lp in (("A", 85, 100), ("B", 65, 80), ("C", 45, 50), ("D", 0, 0)):
        s.append("INSERT INTO model_rating_bands (model_id, rating, min_score, limit_percent) VALUES ('%s', '%s', %s, %s);" % (mid, rating, ms, lp))
open(BASE + "/scoring_db/02_seed.sql", "w", encoding="utf-8").write("\n".join(s) + "\n")
print("OK")
