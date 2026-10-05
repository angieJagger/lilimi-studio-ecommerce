CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    position INTEGER NOT NULL,

    kind VARCHAR(32) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    product_name VARCHAR(200) NOT NULL,

    pattern_id VARCHAR(64),
    pattern_name VARCHAR(200),
    fit VARCHAR(16),
    size VARCHAR(8),
    color VARCHAR(16),
    embroidery_option_id VARCHAR(32),

    quantity INTEGER NOT NULL,
    unit_price_in_grosz BIGINT NOT NULL,
    line_total_in_grosz BIGINT NOT NULL,

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES orders (id),

    CONSTRAINT uq_order_items_position
        UNIQUE (order_id, position),

    CONSTRAINT ck_order_items_position
        CHECK (position >= 1),

    CONSTRAINT ck_order_items_product_id
        CHECK (btrim(product_id) <> ''),

    CONSTRAINT ck_order_items_product_name
        CHECK (btrim(product_name) <> ''),

    CONSTRAINT ck_order_items_amounts
        CHECK (
            quantity BETWEEN 1 AND 99
            AND unit_price_in_grosz > 0
            AND line_total_in_grosz = unit_price_in_grosz * quantity
        ),

    CONSTRAINT ck_order_items_configuration
        CHECK (
            (
                kind = 'digital'
                AND quantity = 1
                AND pattern_id IS NULL
                AND pattern_name IS NULL
                AND fit IS NULL
                AND size IS NULL
                AND color IS NULL
                AND embroidery_option_id IS NULL
            )
            OR
            (
                kind = 'sweatshirt'
                AND pattern_id IS NOT NULL
                AND btrim(pattern_id) <> ''
                AND pattern_name IS NOT NULL
                AND btrim(pattern_name) <> ''
                AND fit IS NOT NULL
                AND fit IN ('women', 'men', 'children')
                AND size IS NOT NULL
                AND btrim(size) <> ''
                AND color IS NOT NULL
                AND color IN ('white', 'navy', 'grey', 'black')
                AND embroidery_option_id IS NOT NULL
                AND btrim(embroidery_option_id) <> ''
            )
        )
);
