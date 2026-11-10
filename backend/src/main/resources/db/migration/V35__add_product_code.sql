ALTER TABLE products
    ADD COLUMN product_code VARCHAR(64);

UPDATE products
SET product_code = id;

ALTER TABLE products
    ADD CONSTRAINT uq_products_product_code UNIQUE (product_code);
