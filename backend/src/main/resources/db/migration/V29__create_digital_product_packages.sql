CREATE TABLE digital_product_packages (
    id UUID PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL
        REFERENCES products(id),
    package_version INTEGER NOT NULL,
    storage_key VARCHAR(160) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    current_package BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_digital_packages_storage_key
        UNIQUE (storage_key),

    CONSTRAINT uq_digital_packages_product_version
        UNIQUE (product_id, package_version),

    CONSTRAINT ck_digital_packages_version
        CHECK (package_version > 0),

    CONSTRAINT ck_digital_packages_byte_size
        CHECK (byte_size > 0),

    CONSTRAINT ck_digital_packages_storage_key
        CHECK (btrim(storage_key) <> ''),

    CONSTRAINT ck_digital_packages_filename
        CHECK (btrim(original_filename) <> ''),

    CONSTRAINT ck_digital_packages_sha256
        CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX uq_digital_packages_current
    ON digital_product_packages(product_id)
    WHERE current_package = TRUE;
