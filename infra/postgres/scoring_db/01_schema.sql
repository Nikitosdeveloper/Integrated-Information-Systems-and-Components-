CREATE TABLE scoring_models (
    id         uuid PRIMARY KEY,
    name       varchar(150) NOT NULL,
    version    int          NOT NULL,
    status     varchar(10)  NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    created_by uuid         NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (name, version)
);
-- одна активная модель
CREATE UNIQUE INDEX uq_active_model ON scoring_models ((true)) WHERE status = 'ACTIVE';

CREATE TABLE model_factors (
    id             bigserial PRIMARY KEY,
    model_id       uuid         NOT NULL REFERENCES scoring_models (id) ON DELETE CASCADE,
    ratio_code     varchar(32)  NOT NULL,
    weight         numeric(6,2) NOT NULL CHECK (weight > 0),
    is_stop_factor boolean      NOT NULL DEFAULT false,
    bad_value      numeric(10,4) NOT NULL,
    good_value     numeric(10,4) NOT NULL,
    stop_value     numeric(10,4),
    UNIQUE (model_id, ratio_code)
);

CREATE TABLE model_rating_bands (
    id            bigserial PRIMARY KEY,
    model_id      uuid         NOT NULL REFERENCES scoring_models (id) ON DELETE CASCADE,
    rating        varchar(2)   NOT NULL,
    min_score     numeric(5,1) NOT NULL,
    limit_percent numeric(5,2) NOT NULL,
    UNIQUE (model_id, rating)
);

CREATE TABLE scoring_results (
    id                uuid PRIMARY KEY,
    application_id    uuid          NOT NULL,
    model_id          uuid          NOT NULL REFERENCES scoring_models (id),
    total_score       numeric(5,1)  NOT NULL,
    rating            varchar(2)    NOT NULL,
    recommended_limit numeric(15,2) NOT NULL,
    stop_triggered    boolean       NOT NULL DEFAULT false,
    calculated_at     timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX idx_scoring_app ON scoring_results (application_id, calculated_at DESC);

CREATE TABLE factor_scores (
    id          bigserial PRIMARY KEY,
    result_id   uuid          NOT NULL REFERENCES scoring_results (id) ON DELETE CASCADE,
    ratio_code  varchar(32)   NOT NULL,
    ratio_value numeric(18,4) NOT NULL,
    score       numeric(5,1)  NOT NULL,
    weight      numeric(6,2)  NOT NULL
);
