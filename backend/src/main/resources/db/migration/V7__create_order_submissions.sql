CREATE TABLE order_submissions (
    idempotency_key UUID PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_order_submissions_order
        FOREIGN KEY (order_id)
        REFERENCES orders (id),

    CONSTRAINT uq_order_submissions_order
        UNIQUE (order_id),

    CONSTRAINT ck_order_submissions_request_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$')
);
