CREATE TABLE industries (
    code varchar(16) PRIMARY KEY,
    name varchar(100) NOT NULL
);

CREATE TABLE companies (
    id            uuid PRIMARY KEY,
    name          varchar(200) NOT NULL,
    unp           varchar(9)   NOT NULL UNIQUE CHECK (unp ~ '^[0-9]{9}$'),
    industry_code varchar(16)  NOT NULL REFERENCES industries (code),
    address       varchar(300),
    phone         varchar(40),
    created_at    timestamptz  NOT NULL DEFAULT now()
);
