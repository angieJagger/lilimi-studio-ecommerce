CREATE TABLE order_payment_attempts (
    id UUID PRIMARY KEY,

    order_id UUID NOT NULL
        REFERENCES orders(id),

    idempotency_key UUID NOT NULL UNIQUE,

    provider VARCHAR(16) NOT NULL DEFAULT 'stripe',
    status VARCHAR(16) NOT NULL DEFAULT 'created',

    amount_in_grosz BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,

    stripe_checkout_session_id VARCHAR(255) UNIQUE,
    stripe_payment_intent_id VARCHAR(255) UNIQUE,

    livemode BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_payment_attempt_provider CHECK (
        provider = 'stripe'
    ),

    CONSTRAINT ck_payment_attempt_status CHECK (
        status IN (
            'created',
            'pending',
            'succeeded',
            'failed',
            'expired'
        )
    ),

    CONSTRAINT ck_payment_attempt_amount CHECK (
        amount_in_grosz > 0
    ),

    CONSTRAINT ck_payment_attempt_currency CHECK (
        currency = 'PLN'
    ),

    CONSTRAINT ck_payment_attempt_session CHECK (
        status = 'created'
        OR stripe_checkout_session_id IS NOT NULL
        OR status = 'failed'
    ),

    CONSTRAINT ck_payment_attempt_paid_at CHECK (
        (status = 'succeeded' AND paid_at IS NOT NULL)
        OR
        (status <> 'succeeded' AND paid_at IS NULL)
    ),

    CONSTRAINT ck_payment_attempt_expiration CHECK (
        expires_at IS NULL OR expires_at > created_at
    ),

    CONSTRAINT ck_payment_attempt_version CHECK (
        version >= 0
    )
);

CREATE INDEX idx_payment_attempts_order_created
    ON order_payment_attempts (order_id, created_at DESC, id DESC);

CREATE UNIQUE INDEX uq_payment_attempts_active_order
    ON order_payment_attempts (order_id)
    WHERE status IN ('created', 'pending');

CREATE TABLE stripe_webhook_events (
    event_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    livemode BOOLEAN NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
