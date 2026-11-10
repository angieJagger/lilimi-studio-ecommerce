ALTER TABLE order_items
    ADD COLUMN physical_options TEXT,
    DROP CONSTRAINT ck_order_items_configuration,
    ADD CONSTRAINT ck_order_items_configuration
        CHECK (
            (
                kind = 'digital'
                AND quantity = 1
                AND pattern_id IS NULL
                AND pattern_name IS NULL
                AND fit IS NULL
                AND size IS NULL
                AND color IS NULL
                AND embroidery_option_id IS NULL
            )
            OR
            (
                kind = 'sweatshirt'
                AND pattern_id IS NOT NULL
                AND btrim(pattern_id) <> ''
                AND pattern_name IS NOT NULL
                AND btrim(pattern_name) <> ''
                AND fit IS NOT NULL
                AND fit IN ('women', 'men', 'children')
                AND size IS NOT NULL
                AND btrim(size) <> ''
                AND color IS NOT NULL
                AND color IN ('white', 'navy', 'grey', 'black')
                AND embroidery_option_id IS NOT NULL
                AND btrim(embroidery_option_id) <> ''
            )
            OR
            (
                kind = 'physical'
                AND pattern_id IS NULL
                AND pattern_name IS NULL
                AND fit IS NULL
                AND size IS NULL
                AND color IS NULL
                AND embroidery_option_id IS NULL
            )
        );
