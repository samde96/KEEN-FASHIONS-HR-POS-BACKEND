ALTER TABLE organizations
  ADD COLUMN default_product_vat_category VARCHAR(1) NOT NULL DEFAULT 'A';

ALTER TABLE products
  ADD COLUMN vat_category VARCHAR(1) NOT NULL DEFAULT 'A';

ALTER TABLE sale_lines
  ADD COLUMN vat_category VARCHAR(1) NOT NULL DEFAULT 'A',
  ADD COLUMN tax_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.1600,
  ADD COLUMN tax_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;

UPDATE sale_lines
SET tax_amount = ROUND(line_total * 0.16, 4)
WHERE tax_amount = 0;

ALTER TABLE organizations
  ADD CONSTRAINT ck_organizations_default_product_vat_category
    CHECK (default_product_vat_category IN ('A', 'G'));

ALTER TABLE products
  ADD CONSTRAINT ck_products_vat_category CHECK (vat_category IN ('A', 'G'));

ALTER TABLE sale_lines
  ADD CONSTRAINT ck_sale_lines_vat_category CHECK (vat_category IN ('A', 'G')),
  ADD CONSTRAINT ck_sale_lines_tax_values_non_negative CHECK (
    tax_rate >= 0
    AND tax_amount >= 0
  );
