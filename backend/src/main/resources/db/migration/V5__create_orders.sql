CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'new',
    language VARCHAR(2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PLN',

    customer_full_name VARCHAR(150) NOT NULL,
    customer_email VARCHAR(254) NOT NULL,
    customer_phone VARCHAR(30),

    delivery_kind VARCHAR(16) NOT NULL,
    delivery_method_id VARCHAR(32),

    address_line1 VARCHAR(200),
    address_line2 VARCHAR(200),
    postal_code VARCHAR(6),
    city VARCHAR(100),
    country_code VARCHAR(2),

    subtotal_in_grosz BIGINT NOT NULL,
    delivery_price_in_grosz BIGINT NOT NULL,
    total_in_grosz BIGINT NOT NULL,

    CONSTRAINT ck_orders_status
        CHECK (status IN ('new', 'processing', 'completed', 'cancelled')),

    CONSTRAINT ck_orders_language
        CHECK (language IN ('pl', 'en')),

    CONSTRAINT ck_orders_currency
        CHECK (currency = 'PLN'),

    CONSTRAINT ck_orders_customer_name
        CHECK (btrim(customer_full_name) <> ''),

    CONSTRAINT ck_orders_customer_email
        CHECK (btrim(customer_email) <> ''),

    CONSTRAINT ck_orders_amounts
        CHECK (
            subtotal_in_grosz > 0
            AND delivery_price_in_grosz >= 0
            AND total_in_grosz = subtotal_in_grosz + delivery_price_in_grosz
        ),

    CONSTRAINT ck_orders_delivery
        CHECK (
            (
                delivery_kind = 'digital'
                AND delivery_method_id IS NULL
                AND address_line1 IS NULL
                AND address_line2 IS NULL
                AND postal_code IS NULL
                AND city IS NULL
                AND country_code IS NULL
                AND delivery_price_in_grosz = 0
            )
            OR
            (
                delivery_kind = 'courier'
                AND delivery_method_id IS NOT NULL
                AND delivery_method_id IN (
                    'dhl-courier',
                    'dpd-courier',
                    'inpost-courier'
                )
                AND address_line1 IS NOT NULL
                AND btrim(address_line1) <> ''
                AND postal_code IS NOT NULL
                AND postal_code ~ '^[0-9]{2}-[0-9]{3}$'
                AND city IS NOT NULL
                AND btrim(city) <> ''
                AND country_code IS NOT NULL
                AND country_code = 'PL'
            )
        )
);

CREATE INDEX idx_orders_created_at
    ON orders (created_at DESC);
