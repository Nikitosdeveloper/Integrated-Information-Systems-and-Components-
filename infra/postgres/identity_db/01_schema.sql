CREATE TABLE permissions (
    code        varchar(64) PRIMARY KEY,
    description text NOT NULL
);

CREATE TABLE roles (
    code varchar(32) PRIMARY KEY,
    name varchar(100) NOT NULL
);

CREATE TABLE role_permissions (
    role_code       varchar(32) NOT NULL REFERENCES roles (code),
    permission_code varchar(64) NOT NULL REFERENCES permissions (code),
    PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE users (
    id            uuid PRIMARY KEY,
    email         varchar(255) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    full_name     varchar(200) NOT NULL,
    role_code     varchar(32)  NOT NULL REFERENCES roles (code),
    company_id    uuid,
    status        varchar(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BLOCKED')),
    created_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_role ON users (role_code);
