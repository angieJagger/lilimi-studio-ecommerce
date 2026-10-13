ALTER TABLE orders
  ADD COLUMN customer_account_id UUID;

ALTER TABLE orders
  ADD CONSTRAINT fk_orders_customer_account
  FOREIGN KEY (customer_account_id)
  REFERENCES user_accounts (id);

CREATE INDEX idx_orders_customer_created_at
  ON orders (customer_account_id, created_at DESC, id DESC);
