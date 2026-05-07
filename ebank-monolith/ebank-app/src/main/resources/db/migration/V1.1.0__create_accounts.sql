-- Accounts table (accounts module)
CREATE TABLE IF NOT EXISTS accounts (
    id                   BIGSERIAL PRIMARY KEY,
    version              BIGINT NOT NULL DEFAULT 0,
    account_number       VARCHAR(20) NOT NULL UNIQUE,
    account_holder_name  VARCHAR(100) NOT NULL,
    email                VARCHAR(100) NOT NULL UNIQUE,
    phone_number         VARCHAR(20),
    account_type         VARCHAR(20) NOT NULL DEFAULT 'SAVINGS',
    balance              DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    address              VARCHAR(200),
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(100),
    modified_by          VARCHAR(100)
);

CREATE INDEX idx_accounts_email          ON accounts(email);
CREATE INDEX idx_accounts_account_number ON accounts(account_number);
CREATE INDEX idx_accounts_status         ON accounts(status);
