CREATE SEQUENCE digital_product_code_seq;

SELECT setval(
    'digital_product_code_seq',
    COALESCE(MAX(substring(product_code FROM '^pattern-([0-9]{1,18})$')::bigint), 0) + 1,
    false
)
FROM products;
