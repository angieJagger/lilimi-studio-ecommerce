ALTER TABLE login_rate_limits
    RENAME TO request_rate_limits;

ALTER INDEX idx_login_rate_limits_expiry
    RENAME TO idx_request_rate_limits_expiry;
