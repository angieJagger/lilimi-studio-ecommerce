CREATE TABLE products (
    id VARCHAR(64) PRIMARY KEY,
    slug VARCHAR(160) NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_products_slug UNIQUE (slug),

    CONSTRAINT ck_products_id_not_blank
        CHECK (btrim(id) <> ''),

    CONSTRAINT ck_products_slug_not_blank
        CHECK (btrim(slug) <> ''),

    CONSTRAINT ck_products_type
        CHECK (
            product_type IN (
                'digital',
                'sweatshirt',
                'tshirt',
                'tote'
            )
        )
);

CREATE TABLE product_translations (
    product_id VARCHAR(64) NOT NULL,
    language VARCHAR(2) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,

    CONSTRAINT pk_product_translations
        PRIMARY KEY (product_id, language),

    CONSTRAINT fk_product_translations_product
        FOREIGN KEY (product_id)
        REFERENCES products (id),

    CONSTRAINT ck_product_translations_language
        CHECK (language IN ('pl', 'en')),

    CONSTRAINT ck_product_translations_name_not_blank
        CHECK (btrim(name) <> ''),

    CONSTRAINT ck_product_translations_description_not_blank
        CHECK (btrim(description) <> '')
);
