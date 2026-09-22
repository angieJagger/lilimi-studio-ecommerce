CREATE TABLE digital_product_prices (
    product_id VARCHAR(64) PRIMARY KEY,
    price_in_grosz INTEGER NOT NULL,

    CONSTRAINT fk_digital_prices_product
        FOREIGN KEY (product_id)
        REFERENCES products (id),

    CONSTRAINT ck_digital_prices_positive
        CHECK (price_in_grosz > 0)
);

CREATE TABLE embroidery_options (
    pattern_id VARCHAR(64) NOT NULL,
    option_id VARCHAR(32) NOT NULL,
    width_mm INTEGER NOT NULL,
    height_mm INTEGER NOT NULL,
    placement VARCHAR(16) NOT NULL,

    CONSTRAINT pk_embroidery_options
        PRIMARY KEY (pattern_id, option_id),

    CONSTRAINT fk_embroidery_options_pattern
        FOREIGN KEY (pattern_id)
        REFERENCES products (id),

    CONSTRAINT ck_embroidery_options_id_not_blank
        CHECK (btrim(option_id) <> ''),

    CONSTRAINT ck_embroidery_options_width
        CHECK (width_mm BETWEEN 50 AND 250),

    CONSTRAINT ck_embroidery_options_height
        CHECK (height_mm BETWEEN 50 AND 420),

    CONSTRAINT ck_embroidery_options_placement
        CHECK (placement IN ('chest', 'back'))
);

CREATE TABLE garment_variants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id VARCHAR(64) NOT NULL,
    pattern_id VARCHAR(64) NOT NULL,
    embroidery_option_id VARCHAR(32) NOT NULL,
    fit VARCHAR(16) NOT NULL,
    size VARCHAR(8) NOT NULL,
    color VARCHAR(16) NOT NULL,
    price_in_grosz INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_garment_variants_product
        FOREIGN KEY (product_id)
        REFERENCES products (id),

    CONSTRAINT fk_garment_variants_embroidery
        FOREIGN KEY (pattern_id, embroidery_option_id)
        REFERENCES embroidery_options (pattern_id, option_id),

    CONSTRAINT uq_garment_variants_configuration
        UNIQUE (
            product_id,
            pattern_id,
            embroidery_option_id,
            fit,
            size,
            color
        ),

    CONSTRAINT ck_garment_variants_fit
        CHECK (fit IN ('women', 'men', 'children')),

    CONSTRAINT ck_garment_variants_size
        CHECK (
            (fit = 'women' AND size IN (
                'XS', 'S', 'M', 'L', 'XL'
            ))
            OR
            (fit = 'men' AND size IN (
                'S', 'M', 'L', 'XL', 'XXL'
            ))
            OR
            (fit = 'children' AND size IN (
                '92', '98', '104', '110', '116',
                '122', '128', '134', '140', '146',
                '152', '158', '164'
            ))
        ),

    CONSTRAINT ck_garment_variants_color
        CHECK (color IN ('white', 'navy', 'grey', 'black')),

    CONSTRAINT ck_garment_variants_price_positive
        CHECK (price_in_grosz > 0)
);
