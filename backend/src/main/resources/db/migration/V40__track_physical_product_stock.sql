ALTER TABLE products ADD COLUMN stock_quantity integer;
ALTER TABLE products ADD CONSTRAINT chk_products_stock_quantity
  CHECK (stock_quantity IS NULL OR (product_type = 'physical' AND stock_quantity >= 0));

ALTER TABLE order_items ADD COLUMN stock_reserved boolean NOT NULL DEFAULT false;
