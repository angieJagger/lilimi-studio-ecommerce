INSERT INTO products (id, slug, product_type, active)
VALUES
    ('pattern-001', 'forest-dragon', 'digital', TRUE),
    ('pattern-002', 'floral-monogram', 'digital', TRUE),
    ('pattern-003', 'butterfly-pattern', 'digital', TRUE),
    ('pattern-004', 'forest-leaves-pattern', 'digital', TRUE),
    ('embroidered-002', 'embroidered-sweatshirt', 'sweatshirt', TRUE);


INSERT INTO product_translations (
    product_id, language, name, description
)
VALUES
    (
        'pattern-001', 'pl',
        'Wzór haftu „Leśny smok”',
        'Plik cyfrowy w formatach DST, PES i JEF.'
    ),
    (
        'pattern-001', 'en',
        'Forest Dragon embroidery pattern',
        'Digital file in DST, PES and JEF formats.'
    ),
    (
        'pattern-002', 'pl',
        'Monogram kwiatowy',
        'Plik cyfrowy w formatach DST, PES i JEF.'
    ),
    (
        'pattern-002', 'en',
        'Floral monogram',
        'Digital file in DST, PES and JEF formats.'
    ),
    (
        'pattern-003', 'pl',
        'Wzór haftu „Motyl”',
        'Plik cyfrowy w formatach DST, PES i JEF.'
    ),
    (
        'pattern-003', 'en',
        'Butterfly embroidery pattern',
        'Digital file in DST, PES and JEF formats.'
    ),
    (
        'pattern-004', 'pl',
        'Wzór haftu „Leśne liście”',
        'Plik cyfrowy w formatach DST, PES i JEF.'
    ),
    (
        'pattern-004', 'en',
        'Forest Leaves embroidery pattern',
        'Digital file in DST, PES and JEF formats.'
    ),
    (
        'embroidered-002', 'pl',
        'Bluza z haftem',
        'Bluza z wybranym haftem, wykonywana na zamówienie.'
    ),
    (
        'embroidered-002', 'en',
        'Embroidered sweatshirt',
        'A sweatshirt with your chosen embroidery, made to order.'
    );


INSERT INTO digital_product_prices (product_id, price_in_grosz)
VALUES
    ('pattern-001', 2900),
    ('pattern-002', 1900),
    ('pattern-003', 2400),
    ('pattern-004', 2200);


INSERT INTO embroidery_options (
    pattern_id, option_id, width_mm, height_mm, placement
)
VALUES
    ('pattern-001', 'small-front', 100, 100, 'chest'),
    ('pattern-001', 'large-back', 200, 200, 'back');


WITH fits_and_sizes (fit, sizes) AS (
    VALUES
        ('women', ARRAY['XS', 'S', 'M', 'L', 'XL']),
        ('men', ARRAY['S', 'M', 'L', 'XL', 'XXL']),
        (
            'children',
            ARRAY[
                '92', '98', '104', '110', '116',
                '122', '128', '134', '140', '146',
                '152', '158', '164'
            ]
        )
),
colors (color) AS (
    VALUES ('white'), ('navy'), ('grey'), ('black')
),
embroidery_prices (option_id, adult_price, child_price) AS (
    VALUES
        ('small-front', 14900, 12900),
        ('large-back', 16900, 14900)
)
INSERT INTO garment_variants (
    product_id,
    pattern_id,
    embroidery_option_id,
    fit,
    size,
    color,
    price_in_grosz,
    active
)
SELECT
    'embroidered-002',
    'pattern-001',
    embroidery_prices.option_id,
    fits_and_sizes.fit,
    garment_size.size,
    colors.color,
    CASE
        WHEN fits_and_sizes.fit = 'children'
            THEN embroidery_prices.child_price
        ELSE embroidery_prices.adult_price
    END,
    TRUE
FROM fits_and_sizes
CROSS JOIN LATERAL unnest(fits_and_sizes.sizes) AS garment_size(size)
CROSS JOIN colors
CROSS JOIN embroidery_prices;
