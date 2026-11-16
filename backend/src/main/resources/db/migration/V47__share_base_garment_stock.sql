CREATE TABLE garment_stock (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id varchar(64) NOT NULL REFERENCES products (id),
    fit varchar(16) NOT NULL,
    size varchar(8) NOT NULL,
    color varchar(16) NOT NULL,
    quantity integer NOT NULL,
    UNIQUE (product_id, fit, size, color),
    UNIQUE (id, product_id, fit, size, color)
);

-- Infer the lowest pre-reservation balance among active variants, then account
-- for every outstanding reservation once. A negative balance can result from
-- legacy overbooking and is repaid as those reservations are released.
WITH reservations AS (
    SELECT reserved_variant_id, SUM(quantity) AS quantity
    FROM order_items
    WHERE stock_reserved AND reserved_variant_id IS NOT NULL
    GROUP BY reserved_variant_id
)
INSERT INTO garment_stock (product_id, fit, size, color, quantity)
SELECT v.product_id, v.fit, v.size, v.color,
       COALESCE(
           MIN(v.stock_quantity + COALESCE(r.quantity, 0)) FILTER (WHERE v.active),
           MIN(v.stock_quantity + COALESCE(r.quantity, 0))
       ) - SUM(COALESCE(r.quantity, 0))
FROM garment_variants v
LEFT JOIN reservations r ON r.reserved_variant_id = v.id
GROUP BY v.product_id, v.fit, v.size, v.color;

ALTER TABLE garment_variants ADD COLUMN stock_id uuid;

UPDATE garment_variants v
SET stock_id = stock.id
FROM garment_stock stock
WHERE (v.product_id, v.fit, v.size, v.color) =
      (stock.product_id, stock.fit, stock.size, stock.color);

ALTER TABLE garment_variants
    ALTER COLUMN stock_id SET NOT NULL,
    ADD CONSTRAINT fk_garment_variants_stock
        FOREIGN KEY (stock_id, product_id, fit, size, color)
        REFERENCES garment_stock (id, product_id, fit, size, color),
    DROP COLUMN stock_quantity;

CREATE INDEX idx_garment_variants_stock ON garment_variants (stock_id);
