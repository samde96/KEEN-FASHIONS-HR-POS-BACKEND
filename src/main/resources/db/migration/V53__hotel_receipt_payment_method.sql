ALTER TABLE hotel_receipt_settings
  ADD COLUMN payment_type VARCHAR(10) NOT NULL DEFAULT 'TILL',
  ADD COLUMN payment_number VARCHAR(30) NOT NULL DEFAULT '';

UPDATE hotel_receipt_settings
SET payment_type = CASE WHEN paybill_number <> '' THEN 'PAYBILL' ELSE 'TILL' END,
    payment_number = CASE
      WHEN paybill_number <> '' THEN paybill_number
      ELSE till_number
    END;

ALTER TABLE hotel_receipt_settings
  ADD CONSTRAINT ck_hotel_receipt_payment_type
  CHECK (payment_type IN ('PAYBILL', 'TILL'));

ALTER TABLE hotel_orders
  ADD COLUMN payment_type VARCHAR(10) NOT NULL DEFAULT 'TILL',
  ADD COLUMN payment_number VARCHAR(30) NOT NULL DEFAULT '';

UPDATE hotel_orders
SET payment_type = CASE WHEN paybill_number <> '' THEN 'PAYBILL' ELSE 'TILL' END,
    payment_number = CASE
      WHEN paybill_number <> '' THEN paybill_number
      ELSE till_number
    END;

ALTER TABLE hotel_orders
  ADD CONSTRAINT ck_hotel_order_payment_type
  CHECK (payment_type IN ('PAYBILL', 'TILL'));
