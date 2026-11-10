ALTER TABLE products
    ADD COLUMN archived boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT chk_products_archived_inactive CHECK (NOT archived OR NOT active);

ALTER TABLE garment_variants
    ADD COLUMN archived boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT chk_garment_variants_archived_inactive CHECK (NOT archived OR NOT active);
