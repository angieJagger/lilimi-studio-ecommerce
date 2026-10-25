CREATE TABLE login_rate_limits (
    bucket_key CHAR(64) PRIMARY KEY,
    attempt_count INTEGER NOT NULL CHECK (attempt_count > 0),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_login_rate_limits_expiry ON login_rate_limits (expires_at);
