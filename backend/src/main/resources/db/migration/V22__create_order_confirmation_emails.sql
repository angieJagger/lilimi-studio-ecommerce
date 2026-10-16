CREATE TABLE order_confirmation_emails (
    order_id UUID PRIMARY KEY
        REFERENCES orders(id) ON DELETE CASCADE,

    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    attempts INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,

    last_error_code VARCHAR(64),

    CONSTRAINT ck_order_confirmation_email_status CHECK (
        status IN ('pending', 'sent', 'failed')
    ),

    CONSTRAINT ck_order_confirmation_email_attempts CHECK (
        attempts >= 0
    ),

    CONSTRAINT ck_order_confirmation_email_sent_at CHECK (
        (status = 'sent' AND sent_at IS NOT NULL)
        OR
        (status IN ('pending', 'failed') AND sent_at IS NULL)
    )
);

CREATE INDEX idx_order_confirmation_emails_pending
    ON order_confirmation_emails (next_attempt_at, created_at)
    WHERE status = 'pending';
