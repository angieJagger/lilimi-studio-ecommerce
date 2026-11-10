ALTER TABLE products
    DROP CONSTRAINT ck_products_type,
    ADD CONSTRAINT ck_products_type
        CHECK (product_type IN ('digital', 'sweatshirt', 'tshirt', 'tote', 'physical')),
    ADD COLUMN physical_configuration TEXT;

ALTER SEQUENCE sweatshirt_product_code_seq RENAME TO physical_product_code_seq;

SELECT setval(
    'physical_product_code_seq',
    COALESCE(MAX(substring(product_code FROM '^physical-([0-9]{1,18})$')::bigint), 0) + 1,
    false
)
FROM products;
