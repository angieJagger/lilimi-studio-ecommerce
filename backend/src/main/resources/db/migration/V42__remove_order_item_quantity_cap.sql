ALTER TABLE order_items DROP CONSTRAINT ck_order_items_amounts;
ALTER TABLE order_items ADD CONSTRAINT ck_order_items_amounts
  CHECK (quantity >= 1 AND unit_price_in_grosz > 0 AND line_total_in_grosz = unit_price_in_grosz * quantity);
