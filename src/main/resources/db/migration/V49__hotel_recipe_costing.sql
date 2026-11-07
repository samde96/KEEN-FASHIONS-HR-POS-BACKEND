ALTER TABLE hotel_items
  ADD COLUMN unit_cost NUMERIC(16,4) CHECK (unit_cost IS NULL OR unit_cost >= 0),
  ADD COLUMN unit_cost_estimated BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE hotel_stock_movements
  ADD COLUMN unit_cost NUMERIC(16,4) CHECK (unit_cost IS NULL OR unit_cost >= 0),
  ADD COLUMN cost_value NUMERIC(16,2) CHECK (cost_value IS NULL OR cost_value >= 0);

ALTER TABLE hotel_menu_items
  ADD CONSTRAINT uq_hotel_menu_item_scope UNIQUE (id, organization_id, branch_id);

ALTER TABLE hotel_items
  ADD CONSTRAINT uq_hotel_item_scope UNIQUE (id, organization_id, branch_id);

CREATE TABLE hotel_recipes (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  menu_item_id UUID NOT NULL,
  version INTEGER NOT NULL CHECK (version > 0),
  is_current BOOLEAN NOT NULL DEFAULT true,
  serving_label VARCHAR(80) NOT NULL,
  created_by UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (menu_item_id, version),
  UNIQUE (id, organization_id, branch_id),
  FOREIGN KEY (menu_item_id, organization_id, branch_id)
    REFERENCES hotel_menu_items(id, organization_id, branch_id)
);

CREATE UNIQUE INDEX uq_hotel_recipe_current
  ON hotel_recipes (menu_item_id)
  WHERE is_current;

CREATE TABLE hotel_recipe_ingredients (
  recipe_id UUID NOT NULL,
  organization_id UUID NOT NULL,
  branch_id UUID NOT NULL,
  item_id UUID NOT NULL,
  quantity NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
  unit VARCHAR(30) NOT NULL,
  optional BOOLEAN NOT NULL DEFAULT false,
  PRIMARY KEY (recipe_id, item_id),
  FOREIGN KEY (recipe_id, organization_id, branch_id)
    REFERENCES hotel_recipes(id, organization_id, branch_id),
  FOREIGN KEY (item_id, organization_id, branch_id)
    REFERENCES hotel_items(id, organization_id, branch_id)
);

CREATE INDEX ix_hotel_recipes_branch
  ON hotel_recipes (organization_id, branch_id, menu_item_id, version DESC);
