CREATE TABLE digital_download_emails (
    order_id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),

    CONSTRAINT fk_digital_download_emails_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id),

    CONSTRAINT ck_digital_download_emails_status
        CHECK (status IN ('pending', 'sent', 'failed', 'skipped')),

    CONSTRAINT ck_digital_download_emails_attempts
        CHECK (attempts >= 0),

    CONSTRAINT ck_digital_download_emails_sent_at
        CHECK (
            (status = 'sent' AND sent_at IS NOT NULL)
            OR
            (status <> 'sent' AND sent_at IS NULL)
        )
);

CREATE INDEX idx_digital_download_emails_pending
    ON digital_download_emails(next_attempt_at, created_at, order_id)
    WHERE status = 'pending';
