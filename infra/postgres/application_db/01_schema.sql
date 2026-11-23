CREATE TABLE credit_products (
    id              uuid PRIMARY KEY,
    name            varchar(150)  NOT NULL,
    min_amount      numeric(15,2) NOT NULL CHECK (min_amount > 0),
    max_amount      numeric(15,2) NOT NULL,
    max_term_months int           NOT NULL CHECK (max_term_months > 0),
    interest_rate   numeric(5,2)  NOT NULL,
    status          varchar(16)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CHECK (max_amount >= min_amount)
);

CREATE TABLE credit_applications (
    id                  uuid PRIMARY KEY,
    company_id          uuid          NOT NULL,
    company_name        varchar(200)  NOT NULL,
    industry_code       varchar(16)   NOT NULL,
    product_id          uuid          NOT NULL REFERENCES credit_products (id),
    amount              numeric(15,2) NOT NULL CHECK (amount > 0),
    term_months         int           NOT NULL CHECK (term_months > 0),
    purpose             text,
    collateral          text,
    status              varchar(16)   NOT NULL CHECK (status IN
        ('DRAFT', 'REVIEW', 'WAITING_DOCS', 'COMMITTEE', 'APPROVED', 'REJECTED', 'CANCELLED')),
    created_by          uuid          NOT NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    submitted_at        timestamptz,
    assigned_analyst_id uuid,
    latest_rating       varchar(2),
    latest_score        numeric(5,1),
    recommended_limit   numeric(15,2)
);
CREATE INDEX idx_app_company ON credit_applications (company_id);
CREATE INDEX idx_app_status ON credit_applications (status);

CREATE TABLE application_status_history (
    id             uuid PRIMARY KEY,
    application_id uuid        NOT NULL REFERENCES credit_applications (id),
    from_status    varchar(16),
    to_status      varchar(16) NOT NULL,
    changed_by     uuid        NOT NULL,
    comment        text,
    changed_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE document_requests (
    id             uuid PRIMARY KEY,
    application_id uuid        NOT NULL REFERENCES credit_applications (id),
    requested_by   uuid        NOT NULL,
    due_date       date        NOT NULL,
    status         varchar(8)  NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    created_at     timestamptz NOT NULL DEFAULT now()
);
-- один открытый запрос документов на заявку
CREATE UNIQUE INDEX uq_open_document_request ON document_requests (application_id) WHERE status = 'OPEN';

CREATE TABLE document_request_items (
    id          uuid PRIMARY KEY,
    request_id  uuid         NOT NULL REFERENCES document_requests (id),
    title       varchar(200) NOT NULL,
    is_required boolean      NOT NULL DEFAULT true,
    status      varchar(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROVIDED')),
    file_name   varchar(255)
);

CREATE TABLE analyst_conclusions (
    id             uuid PRIMARY KEY,
    application_id uuid        NOT NULL UNIQUE REFERENCES credit_applications (id),
    analyst_id     uuid        NOT NULL,
    text           text        NOT NULL,
    recommendation varchar(8)  NOT NULL CHECK (recommendation IN ('APPROVE', 'REJECT')),
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE credit_decisions (
    id              uuid PRIMARY KEY,
    application_id  uuid        NOT NULL UNIQUE REFERENCES credit_applications (id),
    decided_by      uuid        NOT NULL,
    decision        varchar(8)  NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    approved_amount numeric(15,2),
    comment         text,
    decided_at      timestamptz NOT NULL DEFAULT now()
);
