CREATE TABLE admin_mfa (
    account_id UUID PRIMARY KEY
        REFERENCES user_accounts(id) ON DELETE CASCADE,
    encrypted_secret TEXT NOT NULL,
    enabled_at TIMESTAMP WITH TIME ZONE,
    last_used_time_step BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_mfa_recovery_codes (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL
        REFERENCES admin_mfa(account_id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_admin_mfa_recovery_codes_account
    ON admin_mfa_recovery_codes(account_id);
