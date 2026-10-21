CREATE TABLE checkout_expiration_tasks (
    payment_attempt_id UUID PRIMARY KEY
        REFERENCES order_payment_attempts(id),

    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    attempts INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    lease_token UUID,
    lease_until TIMESTAMPTZ,

    finished_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),

    CONSTRAINT ck_checkout_expiration_status CHECK (
        status IN ('pending', 'completed', 'needs_review')
    ),

    CONSTRAINT ck_checkout_expiration_attempts CHECK (
        attempts >= 0
    ),

    CONSTRAINT ck_checkout_expiration_lease CHECK (
        (lease_token IS NULL AND lease_until IS NULL)
        OR
        (
            status = 'pending'
            AND lease_token IS NOT NULL
            AND lease_until IS NOT NULL
        )
    ),

    CONSTRAINT ck_checkout_expiration_finished CHECK (
        (status = 'pending' AND finished_at IS NULL)
        OR
        (
            status IN ('completed', 'needs_review')
            AND finished_at IS NOT NULL
        )
    ),

    CONSTRAINT ck_checkout_expiration_review_reason CHECK (
        status <> 'needs_review'
        OR last_error_code IS NOT NULL
    )
);

CREATE INDEX idx_checkout_expiration_pending
    ON checkout_expiration_tasks (next_attempt_at, created_at)
    WHERE status = 'pending';
