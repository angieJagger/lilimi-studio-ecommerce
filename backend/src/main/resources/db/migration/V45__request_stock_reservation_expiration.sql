ALTER TABLE orders
    ADD COLUMN reservation_expiration_requested boolean NOT NULL DEFAULT false;
