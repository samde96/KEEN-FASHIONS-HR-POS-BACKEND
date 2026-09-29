CREATE TABLE stock_adjustments (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  product_id UUID NOT NULL REFERENCES products(id),
  adjusted_by_user_id UUID REFERENCES users(id),
  system_quantity INTEGER NOT NULL,
  counted_quantity INTEGER NOT NULL,
  variance_quantity INTEGER NOT NULL,
  unit_cost NUMERIC(14, 2) NOT NULL,
  loss_value NUMERIC(14, 2) NOT NULL,
  excess_value NUMERIC(14, 2) NOT NULL,
  reason VARCHAR(160) NOT NULL,
  notes VARCHAR(500),
  adjusted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_stock_adjustments_quantities_non_negative CHECK (
    system_quantity >= 0 AND counted_quantity >= 0
  ),
  CONSTRAINT ck_stock_adjustments_unit_cost_non_negative CHECK (unit_cost >= 0),
  CONSTRAINT ck_stock_adjustments_values_non_negative CHECK (
    loss_value >= 0 AND excess_value >= 0
  ),
  CONSTRAINT ck_stock_adjustments_single_value_side CHECK (
    (variance_quantity < 0 AND loss_value > 0 AND excess_value = 0)
    OR (variance_quantity > 0 AND excess_value > 0 AND loss_value = 0)
    OR (variance_quantity = 0 AND loss_value = 0 AND excess_value = 0)
  )
);

CREATE INDEX ix_stock_adjustments_organization_adjusted
  ON stock_adjustments (organization_id, adjusted_at DESC);

CREATE INDEX ix_stock_adjustments_branch_adjusted
  ON stock_adjustments (branch_id, adjusted_at DESC);

CREATE INDEX ix_stock_adjustments_product_adjusted
  ON stock_adjustments (product_id, adjusted_at DESC);
