WITH missing_products AS (
  SELECT
    id,
    organization_id,
    row_number() OVER (PARTITION BY organization_id ORDER BY created_at, id) AS sequence
  FROM products
  WHERE barcode IS NULL OR btrim(barcode) = ''
),
barcode_seeds AS (
  SELECT
    id,
    organization_id,
    '200' || lpad((900000000 + sequence)::text, 9, '0') AS first_twelve_digits
  FROM missing_products
),
barcode_check_digits AS (
  SELECT
    seed.id,
    seed.organization_id,
    seed.first_twelve_digits,
    (
      10 - (
        sum(
          substring(seed.first_twelve_digits, digit_position, 1)::integer
          * CASE WHEN digit_position % 2 = 1 THEN 1 ELSE 3 END
        ) % 10
      )
    ) % 10 AS check_digit
  FROM barcode_seeds seed
  CROSS JOIN generate_series(1, 12) AS digits(digit_position)
  GROUP BY seed.id, seed.organization_id, seed.first_twelve_digits
)
UPDATE products product
SET
  barcode = barcode_check_digits.first_twelve_digits || barcode_check_digits.check_digit::text,
  updated_at = now()
FROM barcode_check_digits
WHERE product.id = barcode_check_digits.id;
