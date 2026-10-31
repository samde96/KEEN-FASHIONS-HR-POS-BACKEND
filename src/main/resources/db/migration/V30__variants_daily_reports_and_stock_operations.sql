ALTER TABLE products
  ADD COLUMN product_type VARCHAR(24) NOT NULL DEFAULT 'SIMPLE',
  ADD COLUMN parent_product_id UUID REFERENCES products(id),
  ADD COLUMN variant_attributes TEXT NOT NULL DEFAULT '{}',
  ADD COLUMN default_reorder_level INTEGER NOT NULL DEFAULT 0,
  ADD CONSTRAINT ck_product_type CHECK (product_type IN ('SIMPLE', 'VARIABLE')),
  ADD CONSTRAINT ck_product_parent CHECK (parent_product_id IS NULL OR (parent_product_id <> id AND product_type = 'SIMPLE')),
  ADD CONSTRAINT ck_product_reorder CHECK (default_reorder_level >= 0);
CREATE INDEX ix_products_parent ON products (organization_id, parent_product_id);
CREATE UNIQUE INDEX ux_products_sku_case ON products (organization_id, lower(sku));
CREATE UNIQUE INDEX ux_products_barcode_case ON products (organization_id, lower(barcode)) WHERE barcode IS NOT NULL;

ALTER TABLE sale_lines
  ADD COLUMN parent_product_id UUID,
  ADD COLUMN parent_product_name VARCHAR(180),
  ADD COLUMN variant_attributes TEXT NOT NULL DEFAULT '{}';
ALTER TABLE sale_lines ALTER COLUMN product_name TYPE TEXT;
ALTER TABLE sales ADD COLUMN cashier_id UUID REFERENCES users(id);
ALTER TABLE payments DROP CONSTRAINT ck_payments_method;
ALTER TABLE payments ADD CONSTRAINT ck_payments_method CHECK (method IN ('MPESA','CASH','CARD','BANK_TRANSFER'));

CREATE TABLE pos_preferences (
  organization_id UUID PRIMARY KEY REFERENCES organizations(id),
  daily_summary_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  recipient_ids TEXT NOT NULL DEFAULT '[]',
  inactivity_minutes INTEGER NOT NULL DEFAULT 5 CHECK (inactivity_minutes BETWEEN 1 AND 120),
  long_entry_minutes INTEGER NOT NULL DEFAULT 10 CHECK (long_entry_minutes BETWEEN 1 AND 480),
  schedule_started_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO pos_preferences (organization_id) SELECT id FROM organizations;

CREATE TABLE daily_sales_reports (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  business_date DATE NOT NULL,
  time_zone VARCHAR(64) NOT NULL,
  from_inclusive TIMESTAMPTZ NOT NULL,
  to_exclusive TIMESTAMPTZ NOT NULL,
  snapshot TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, branch_id, business_date)
);
CREATE TABLE daily_summary_deliveries (
  id UUID PRIMARY KEY,
  report_id UUID NOT NULL REFERENCES daily_sales_reports(id),
  user_id UUID NOT NULL REFERENCES users(id),
  channel VARCHAR(16) NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL')),
  batch_key VARCHAR(120) NOT NULL DEFAULT 'scheduled',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at TIMESTAMPTZ,
  last_error VARCHAR(250),
  UNIQUE (report_id, user_id, channel, batch_key)
);
CREATE INDEX ix_daily_delivery_due ON daily_summary_deliveries (status, next_attempt_at);
ALTER TABLE notifications ALTER COLUMN message TYPE TEXT;

CREATE TABLE stock_operations (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  user_id UUID NOT NULL REFERENCES users(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'COMPLETED', 'DISCARDED')),
  payload TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_stock_operations_owner ON stock_operations (organization_id, user_id, status);

CREATE TABLE sale_reversals (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  sale_id UUID NOT NULL UNIQUE REFERENCES sales(id),
  user_id UUID NOT NULL REFERENCES users(id),
  kind VARCHAR(16) NOT NULL CHECK (kind IN ('REFUND', 'CANCELLATION')),
  reason VARCHAR(500) NOT NULL,
  amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
  tax_amount NUMERIC(19,4) NOT NULL CHECK (tax_amount >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reversals_period ON sale_reversals (organization_id, branch_id, created_at);
