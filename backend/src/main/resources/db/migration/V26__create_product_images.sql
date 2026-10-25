ALTER TABLE products
    ADD COLUMN images_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE product_images (
    id UUID PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL
        REFERENCES products(id) ON DELETE CASCADE,
    storage_key VARCHAR(160) NOT NULL UNIQUE,
    content_type VARCHAR(32) NOT NULL,
    byte_size BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    primary_image BOOLEAN NOT NULL DEFAULT FALSE,
    alt_pl VARCHAR(250) NOT NULL DEFAULT '',
    alt_en VARCHAR(250) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT product_images_content_type_check
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT product_images_byte_size_check
        CHECK (byte_size > 0),
    CONSTRAINT product_images_dimensions_check
        CHECK (width > 0 AND height > 0),
    CONSTRAINT product_images_sort_order_check
        CHECK (sort_order >= 0)
);

CREATE INDEX idx_product_images_product_order
    ON product_images(product_id, sort_order, id);

CREATE UNIQUE INDEX uq_product_images_primary
    ON product_images(product_id)
    WHERE primary_image = TRUE;
