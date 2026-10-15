CREATE TABLE customer_profiles (
    account_id UUID PRIMARY KEY
        REFERENCES user_accounts(id) ON DELETE CASCADE,

    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(30),

    address_line1 VARCHAR(200),
    address_line2 VARCHAR(200),
    postal_code VARCHAR(6),
    city VARCHAR(100),
    country_code VARCHAR(2),

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_customer_profile_name_length CHECK (
        length(
            trim(
                coalesce(first_name, '') || ' ' ||
                coalesce(last_name, '')
            )
        ) <= 150
    ),

    CONSTRAINT ck_customer_profile_address CHECK (
        (
            address_line1 IS NULL
            AND address_line2 IS NULL
            AND postal_code IS NULL
            AND city IS NULL
            AND country_code IS NULL
        )
        OR
        (
            address_line1 IS NOT NULL
            AND length(trim(address_line1)) > 0
            AND postal_code IS NOT NULL
            AND postal_code ~ '^[0-9]{2}-[0-9]{3}$'
            AND city IS NOT NULL
            AND length(trim(city)) > 0
            AND country_code IS NOT NULL
            AND country_code = 'PL'
        )
    ),

    CONSTRAINT ck_customer_profile_version CHECK (version >= 0)
);
