-- Transactions table (transactions module — replaces MongoDB)
CREATE TABLE IF NOT EXISTS transactions (
    id                BIGSERIAL PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    from_account_id   BIGINT REFERENCES accounts(id),
    to_account_id     BIGINT REFERENCES accounts(id),
    amount            DECIMAL(15, 2) NOT NULL,
    type              VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    description       TEXT,
    reference_number  VARCHAR(36) NOT NULL UNIQUE,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(100),
    modified_by       VARCHAR(100)
);

CREATE INDEX idx_txn_from_account   ON transactions(from_account_id);
CREATE INDEX idx_txn_to_account     ON transactions(to_account_id);
CREATE INDEX idx_txn_reference      ON transactions(reference_number);
CREATE INDEX idx_txn_status         ON transactions(status);
CREATE INDEX idx_txn_type           ON transactions(type);
