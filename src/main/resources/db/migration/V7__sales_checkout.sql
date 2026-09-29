CREATE TABLE sales (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  sale_number VARCHAR(40) NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  customer_name VARCHAR(160),
  subtotal_amount NUMERIC(19, 4) NOT NULL,
  discount_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
  total_amount NUMERIC(19, 4) NOT NULL,
  status VARCHAR(32) NOT NULL,
  sold_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_sales_amounts_non_negative CHECK (
    subtotal_amount >= 0
    AND discount_amount >= 0
    AND tax_amount >= 0
    AND total_amount >= 0
  ),
  CONSTRAINT ck_sales_status CHECK (status IN ('COMPLETED')),
  CONSTRAINT ux_sales_organization_sale_number UNIQUE (organization_id, sale_number),
  CONSTRAINT ux_sales_organization_idempotency UNIQUE (organization_id, idempotency_key)
);

CREATE TABLE sale_lines (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  sale_id UUID NOT NULL REFERENCES sales(id),
  product_id UUID NOT NULL REFERENCES products(id),
  product_name VARCHAR(180) NOT NULL,
  sku VARCHAR(80) NOT NULL,
  category_name VARCHAR(120) NOT NULL,
  quantity INTEGER NOT NULL,
  unit_price NUMERIC(19, 4) NOT NULL,
  cost_price NUMERIC(19, 4),
  line_total NUMERIC(19, 4) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_sale_lines_quantity_positive CHECK (quantity > 0),
  CONSTRAINT ck_sale_lines_amounts_non_negative CHECK (
    unit_price >= 0
    AND (cost_price IS NULL OR cost_price >= 0)
    AND line_total >= 0
  )
);

CREATE TABLE payments (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  sale_id UUID NOT NULL REFERENCES sales(id),
  method VARCHAR(32) NOT NULL,
  amount NUMERIC(19, 4) NOT NULL,
  status VARCHAR(32) NOT NULL,
  reference VARCHAR(80),
  cash_received NUMERIC(19, 4),
  change_due NUMERIC(19, 4),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_payments_method CHECK (method IN ('MPESA', 'CASH', 'CARD')),
  CONSTRAINT ck_payments_status CHECK (status IN ('SETTLED')),
  CONSTRAINT ck_payments_amounts_non_negative CHECK (
    amount >= 0
    AND (cash_received IS NULL OR cash_received >= 0)
    AND (change_due IS NULL OR change_due >= 0)
  )
);

CREATE INDEX ix_sales_organization_sold_at
  ON sales (organization_id, sold_at DESC);

CREATE INDEX ix_sales_branch_sold_at
  ON sales (branch_id, sold_at DESC);

CREATE INDEX ix_sale_lines_sale
  ON sale_lines (sale_id);

CREATE INDEX ix_sale_lines_product
  ON sale_lines (product_id);

CREATE INDEX ix_payments_sale
  ON payments (sale_id);

CREATE INDEX ix_payments_branch_created
  ON payments (branch_id, created_at DESC);
