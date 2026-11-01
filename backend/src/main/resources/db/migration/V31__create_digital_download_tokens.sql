CREATE TABLE digital_download_tokens (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT fk_digital_download_tokens_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id),

    CONSTRAINT uq_digital_download_tokens_hash
        UNIQUE (token_hash),

    CONSTRAINT ck_digital_download_tokens_hash
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_digital_download_tokens_expiration
        CHECK (expires_at > created_at),

    CONSTRAINT ck_digital_download_tokens_revocation
        CHECK (
            revoked_at IS NULL
            OR revoked_at >= created_at
        )
);

CREATE INDEX idx_digital_download_tokens_order
    ON digital_download_tokens(order_id);

CREATE INDEX idx_digital_download_tokens_expiration
    ON digital_download_tokens(expires_at);
