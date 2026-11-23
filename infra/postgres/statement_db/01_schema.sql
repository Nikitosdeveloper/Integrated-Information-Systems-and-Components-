CREATE TABLE financial_statements (
    id          uuid PRIMARY KEY,
    company_id  uuid        NOT NULL,
    form_type   varchar(12) NOT NULL CHECK (form_type IN ('BALANCE', 'PROFIT_LOSS')),
    period_year int         NOT NULL CHECK (period_year BETWEEN 2000 AND 2100),
    status      varchar(8)  NOT NULL CHECK (status IN ('VALID', 'INVALID')),
    created_by  uuid        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (company_id, form_type, period_year)
);

CREATE TABLE statement_lines (
    id           bigserial PRIMARY KEY,
    statement_id uuid          NOT NULL REFERENCES financial_statements (id) ON DELETE CASCADE,
    code         varchar(40)   NOT NULL,
    name         varchar(200)  NOT NULL,
    value        numeric(18,2) NOT NULL,
    UNIQUE (statement_id, code)
);

CREATE TABLE validation_issues (
    id           bigserial PRIMARY KEY,
    statement_id uuid        NOT NULL REFERENCES financial_statements (id) ON DELETE CASCADE,
    severity     varchar(8)  NOT NULL CHECK (severity IN ('ERROR', 'WARNING')),
    message      text        NOT NULL
);
