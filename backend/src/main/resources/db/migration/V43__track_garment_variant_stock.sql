ALTER TABLE garment_variants ADD COLUMN stock_quantity integer NOT NULL DEFAULT 1
  CHECK (stock_quantity >= 0);

UPDATE products SET stock_quantity = NULL WHERE product_type = 'sweatshirt';
ALTER TABLE products DROP CONSTRAINT chk_products_stock_quantity;
ALTER TABLE products ADD CONSTRAINT chk_products_stock_quantity
  CHECK (stock_quantity IS NULL OR (product_type = 'physical' AND stock_quantity >= 0));

ALTER TABLE order_items ADD COLUMN reserved_variant_id uuid;
UPDATE order_items SET stock_reserved = false WHERE kind = 'sweatshirt';
