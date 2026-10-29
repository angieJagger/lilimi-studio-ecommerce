ALTER TABLE order_items
    ADD COLUMN digital_package_id UUID;

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_digital_package
        FOREIGN KEY (digital_package_id)
        REFERENCES digital_product_packages(id);

ALTER TABLE order_items
    ADD CONSTRAINT ck_order_items_digital_package_kind
        CHECK (
            digital_package_id IS NULL
            OR kind = 'digital'
        );

CREATE INDEX idx_order_items_digital_package
    ON order_items(digital_package_id)
    WHERE digital_package_id IS NOT NULL;
