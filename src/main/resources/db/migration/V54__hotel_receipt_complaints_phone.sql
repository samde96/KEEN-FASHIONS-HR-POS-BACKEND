ALTER TABLE hotel_receipt_settings
  ADD COLUMN complaints_phone VARCHAR(40) NOT NULL DEFAULT '';

UPDATE hotel_receipt_settings
SET complaints_phone = contact_phone;

ALTER TABLE hotel_orders
  ADD COLUMN complaints_phone VARCHAR(40) NOT NULL DEFAULT '';

UPDATE hotel_orders
SET complaints_phone = contact_phone;
