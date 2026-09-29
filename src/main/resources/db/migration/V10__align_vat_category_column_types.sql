ALTER TABLE organizations
  ALTER COLUMN default_product_vat_category TYPE CHAR(1)
  USING default_product_vat_category::CHAR(1);

ALTER TABLE products
  ALTER COLUMN vat_category TYPE CHAR(1)
  USING vat_category::CHAR(1);

ALTER TABLE sale_lines
  ALTER COLUMN vat_category TYPE CHAR(1)
  USING vat_category::CHAR(1);
