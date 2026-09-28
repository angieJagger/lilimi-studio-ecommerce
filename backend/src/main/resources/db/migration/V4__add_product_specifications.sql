ALTER TABLE products
    ADD COLUMN made_to_order BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN personalization_available BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE product_file_formats (
    product_id VARCHAR(64) NOT NULL,
    file_format VARCHAR(16) NOT NULL,

    CONSTRAINT pk_product_file_formats
        PRIMARY KEY (product_id, file_format),

    CONSTRAINT fk_product_file_formats_product
        FOREIGN KEY (product_id)
        REFERENCES products (id),

    CONSTRAINT ck_product_file_formats_not_blank
        CHECK (btrim(file_format) <> '')
);

INSERT INTO product_file_formats (product_id, file_format)
SELECT p.id, formats.file_format
FROM products p
CROSS JOIN (
    VALUES ('DST'), ('PES'), ('JEF')
) AS formats(file_format)
WHERE p.id IN (
    'pattern-001',
    'pattern-002',
    'pattern-003',
    'pattern-004'
);

UPDATE products
SET made_to_order = TRUE,
    personalization_available = TRUE
WHERE id = 'embroidered-002';
