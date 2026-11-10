CREATE SEQUENCE sweatshirt_product_code_seq;

SELECT setval(
    'sweatshirt_product_code_seq',
    COALESCE(MAX(substring(product_code FROM '^sweatshirt-([0-9]{1,18})$')::bigint), 0) + 1,
    false
)
FROM products;
