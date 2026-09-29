UPDATE products product
SET cost_price = COALESCE(
  product.cost_price,
  (
    SELECT intake.unit_cost
    FROM stock_intakes intake
    WHERE intake.product_id = product.id
      AND intake.unit_cost IS NOT NULL
    ORDER BY intake.received_at DESC
    LIMIT 1
  ),
  product.unit_price,
  0
)
WHERE product.cost_price IS NULL;

ALTER TABLE products
  ALTER COLUMN cost_price SET NOT NULL;

UPDATE stock_intakes intake
SET unit_cost = product.cost_price
FROM products product
WHERE intake.product_id = product.id
  AND intake.unit_cost IS NULL;

ALTER TABLE stock_intakes
  ALTER COLUMN unit_cost SET NOT NULL;

UPDATE sale_lines line
SET cost_price = product.cost_price
FROM products product
WHERE line.product_id = product.id
  AND line.cost_price IS NULL;

ALTER TABLE sale_lines
  ALTER COLUMN cost_price SET NOT NULL;
