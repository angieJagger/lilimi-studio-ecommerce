ALTER TABLE user_accounts
  ADD COLUMN credentials_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE password_reset_tokens (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  credentials_version BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,

  CONSTRAINT fk_password_reset_account
    FOREIGN KEY (account_id)
    REFERENCES user_accounts (id)
    ON DELETE CASCADE,

  CONSTRAINT uq_password_reset_token_hash
    UNIQUE (token_hash),

  CONSTRAINT ck_password_reset_expiry
    CHECK (expires_at > created_at),

  CONSTRAINT ck_password_reset_consumed
    CHECK (consumed_at IS NULL OR consumed_at >= created_at),

  CONSTRAINT ck_password_reset_credentials_version
    CHECK (credentials_version >= 0)
);

CREATE INDEX idx_password_reset_account_created
  ON password_reset_tokens (account_id, created_at DESC);
