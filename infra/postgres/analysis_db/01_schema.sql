CREATE TABLE ratio_definitions (
    code             varchar(32) PRIMARY KEY,
    name             varchar(100) NOT NULL,
    formula          varchar(200) NOT NULL,
    higher_is_better boolean      NOT NULL
);

CREATE TABLE benchmark_sets (
    id         uuid PRIMARY KEY,
    name       varchar(150) NOT NULL,
    status     varchar(10)  NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    created_at timestamptz  NOT NULL DEFAULT now()
);
-- один активный набор нормативов
CREATE UNIQUE INDEX uq_active_benchmark_set ON benchmark_sets ((true)) WHERE status = 'ACTIVE';

CREATE TABLE industry_benchmarks (
    id             bigserial PRIMARY KEY,
    set_id         uuid          NOT NULL REFERENCES benchmark_sets (id) ON DELETE CASCADE,
    industry_code  varchar(16)   NOT NULL,   -- ALL - значение по умолчанию для отраслей без отдельных нормативов
    ratio_code     varchar(32)   NOT NULL REFERENCES ratio_definitions (code),
    good_threshold numeric(10,4) NOT NULL,
    ok_threshold   numeric(10,4) NOT NULL,
    UNIQUE (set_id, industry_code, ratio_code)
);

CREATE TABLE financial_analyses (
    id               uuid PRIMARY KEY,
    application_id   uuid        NOT NULL UNIQUE,
    company_id       uuid        NOT NULL,
    benchmark_set_id uuid        NOT NULL REFERENCES benchmark_sets (id),
    calculated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ratio_values (
    id          bigserial PRIMARY KEY,
    analysis_id uuid          NOT NULL REFERENCES financial_analyses (id) ON DELETE CASCADE,
    ratio_code  varchar(32)   NOT NULL REFERENCES ratio_definitions (code),
    period_year int           NOT NULL,
    value       numeric(18,4) NOT NULL,
    assessment  varchar(8)    CHECK (assessment IN ('GOOD', 'NORMAL', 'BAD')),
    UNIQUE (analysis_id, ratio_code, period_year)
);
