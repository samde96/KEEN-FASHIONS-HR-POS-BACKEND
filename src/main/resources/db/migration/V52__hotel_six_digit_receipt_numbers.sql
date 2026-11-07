DO $$
BEGIN
  IF (SELECT count(*) FROM hotel_orders) > 999999 THEN
    RAISE EXCEPTION 'Cannot assign six-digit receipt numbers: hotel order count exceeds 999999';
  END IF;
END $$;

CREATE SEQUENCE hotel_receipt_number_seq
  AS INTEGER
  START WITH 1
  INCREMENT BY 1
  MINVALUE 1
  MAXVALUE 999999
  NO CYCLE;

ALTER TABLE hotel_orders ADD COLUMN receipt_number VARCHAR(6);

WITH numbered_orders AS (
  SELECT id, row_number() OVER (ORDER BY created_at, id) AS receipt_number
  FROM hotel_orders
)
UPDATE hotel_orders AS orders
SET receipt_number = lpad(numbered_orders.receipt_number::text, 6, '0')
FROM numbered_orders
WHERE orders.id = numbered_orders.id;

ALTER TABLE hotel_orders ALTER COLUMN receipt_number SET NOT NULL;
ALTER TABLE hotel_orders ADD CONSTRAINT uq_hotel_order_receipt_number UNIQUE (receipt_number);

SELECT setval(
  'hotel_receipt_number_seq',
  coalesce(max(receipt_number::integer), 1),
  count(*) > 0
)
FROM hotel_orders;
