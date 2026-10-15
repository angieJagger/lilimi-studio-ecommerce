ALTER TABLE user_accounts
  ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN email_verification_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_accounts
  ALTER COLUMN email_verification_required SET DEFAULT TRUE;

CREATE TABLE email_verification_tokens (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,

  CONSTRAINT fk_email_verification_account
    FOREIGN KEY (account_id)
    REFERENCES user_accounts (id)
    ON DELETE CASCADE,

  CONSTRAINT uq_email_verification_token_hash
    UNIQUE (token_hash),

  CONSTRAINT ck_email_verification_token_expiry
    CHECK (expires_at > created_at),

  CONSTRAINT ck_email_verification_token_consumed
    CHECK (consumed_at IS NULL OR consumed_at >= created_at)
);

CREATE INDEX idx_email_verification_account
  ON email_verification_tokens (account_id);
