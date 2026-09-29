CREATE TABLE product_categories (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  name VARCHAR(120) NOT NULL,
  code VARCHAR(40) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_product_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT ux_product_categories_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE products (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  category_id UUID NOT NULL REFERENCES product_categories(id),
  name VARCHAR(180) NOT NULL,
  sku VARCHAR(80) NOT NULL,
  barcode VARCHAR(80),
  department VARCHAR(80),
  description VARCHAR(500),
  unit_price NUMERIC(14, 2) NOT NULL,
  cost_price NUMERIC(14, 2),
  sizes VARCHAR(500) NOT NULL DEFAULT '',
  colors VARCHAR(500) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT ck_products_unit_price_non_negative CHECK (unit_price >= 0),
  CONSTRAINT ck_products_cost_price_non_negative CHECK (cost_price IS NULL OR cost_price >= 0),
  CONSTRAINT ux_products_organization_sku UNIQUE (organization_id, sku)
);

CREATE TABLE inventory_items (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  product_id UUID NOT NULL REFERENCES products(id),
  quantity_on_hand INTEGER NOT NULL DEFAULT 0,
  quantity_reserved INTEGER NOT NULL DEFAULT 0,
  reorder_level INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_inventory_quantities_non_negative CHECK (
    quantity_on_hand >= 0 AND quantity_reserved >= 0 AND reorder_level >= 0
  ),
  CONSTRAINT ux_inventory_branch_product UNIQUE (branch_id, product_id)
);

CREATE TABLE stock_intakes (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  product_id UUID NOT NULL REFERENCES products(id),
  supplier_name VARCHAR(160) NOT NULL,
  reference_number VARCHAR(80),
  quantity INTEGER NOT NULL,
  unit_cost NUMERIC(14, 2),
  notes VARCHAR(500),
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_stock_intakes_quantity_positive CHECK (quantity > 0),
  CONSTRAINT ck_stock_intakes_unit_cost_non_negative CHECK (unit_cost IS NULL OR unit_cost >= 0)
);

CREATE TABLE stock_transfers (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  source_branch_id UUID NOT NULL REFERENCES branches(id),
  destination_branch_id UUID NOT NULL REFERENCES branches(id),
  product_id UUID NOT NULL REFERENCES products(id),
  quantity INTEGER NOT NULL,
  status VARCHAR(32) NOT NULL,
  reference_number VARCHAR(80),
  notes VARCHAR(500),
  transferred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_stock_transfers_quantity_positive CHECK (quantity > 0),
  CONSTRAINT ck_stock_transfers_status CHECK (status IN ('COMPLETED', 'CANCELLED')),
  CONSTRAINT ck_stock_transfers_distinct_branches CHECK (source_branch_id <> destination_branch_id)
);

CREATE INDEX ix_product_categories_organization_status
  ON product_categories (organization_id, status);

CREATE INDEX ix_products_organization_status
  ON products (organization_id, status);

CREATE INDEX ix_products_category
  ON products (category_id);

CREATE INDEX ix_inventory_organization_branch
  ON inventory_items (organization_id, branch_id);

CREATE INDEX ix_inventory_product
  ON inventory_items (product_id);

CREATE INDEX ix_stock_intakes_organization_received
  ON stock_intakes (organization_id, received_at DESC);

CREATE INDEX ix_stock_transfers_organization_transferred
  ON stock_transfers (organization_id, transferred_at DESC);
