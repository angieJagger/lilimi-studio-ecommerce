CREATE TABLE user_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_user_accounts_email
        UNIQUE (email),

    CONSTRAINT ck_user_accounts_email
        CHECK (
            btrim(email) <> ''
            AND email = lower(btrim(email))
        ),

    CONSTRAINT ck_user_accounts_password_hash
        CHECK (btrim(password_hash) <> ''),

    CONSTRAINT ck_user_accounts_role
        CHECK (role IN ('ADMIN', 'CUSTOMER'))
);
