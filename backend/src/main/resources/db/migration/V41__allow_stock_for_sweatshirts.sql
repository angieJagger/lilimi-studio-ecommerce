ALTER TABLE products DROP CONSTRAINT chk_products_stock_quantity;
ALTER TABLE products ADD CONSTRAINT chk_products_stock_quantity
  CHECK (stock_quantity IS NULL OR (product_type IN ('physical', 'sweatshirt') AND stock_quantity >= 0));
