-- Notifications table (notifications module)
CREATE TABLE IF NOT EXISTS notifications (
    id              BIGSERIAL PRIMARY KEY,
    version         BIGINT NOT NULL DEFAULT 0,
    recipient_email VARCHAR(100) NOT NULL,
    subject         VARCHAR(200),
    body            TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_id  BIGINT REFERENCES transactions(id),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100),
    modified_by     VARCHAR(100)
);

CREATE INDEX idx_notifications_status         ON notifications(status);
CREATE INDEX idx_notifications_transaction_id ON notifications(transaction_id);
