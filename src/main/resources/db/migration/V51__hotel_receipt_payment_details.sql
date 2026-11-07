ALTER TABLE hotel_receipt_settings
  ADD COLUMN paybill_number VARCHAR(30) NOT NULL DEFAULT '',
  ADD COLUMN account_number VARCHAR(80) NOT NULL DEFAULT '';

ALTER TABLE hotel_orders
  ADD COLUMN paybill_number VARCHAR(30) NOT NULL DEFAULT '',
  ADD COLUMN account_number VARCHAR(80) NOT NULL DEFAULT '';
