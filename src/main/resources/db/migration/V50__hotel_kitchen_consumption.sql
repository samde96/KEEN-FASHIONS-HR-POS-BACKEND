ALTER TABLE hotel_orders
  ADD COLUMN kitchen_status VARCHAR(15) NOT NULL DEFAULT 'PENDING'
    CHECK (kitchen_status IN ('PENDING', 'PREPARING', 'READY', 'SERVED', 'CANCELLED'));

UPDATE hotel_orders
SET kitchen_status = CASE
  WHEN status = 'CANCELLED' THEN 'CANCELLED'
  WHEN status = 'CLOSED' THEN 'READY'
  ELSE 'PENDING'
END;

ALTER TABLE hotel_order_lines
  ADD COLUMN prepared_quantity NUMERIC(14,3) NOT NULL DEFAULT 0
    CHECK (prepared_quantity >= 0 AND prepared_quantity <= quantity),
  ADD COLUMN recipe_id UUID REFERENCES hotel_recipes(id);

ALTER TABLE hotel_stock_movements
  DROP CONSTRAINT IF EXISTS hotel_stock_movements_kind_check,
  ADD CONSTRAINT hotel_stock_movements_kind_check
    CHECK (kind IN ('RECEIPT', 'USAGE', 'SALE', 'RETURN', 'KITCHEN'));

CREATE TABLE hotel_order_line_recipe_ingredients (
  order_line_id UUID NOT NULL REFERENCES hotel_order_lines(id),
  item_id UUID NOT NULL REFERENCES hotel_items(id),
  quantity_per_serving NUMERIC(14,6) NOT NULL CHECK (quantity_per_serving > 0),
  stock_unit VARCHAR(30) NOT NULL,
  PRIMARY KEY (order_line_id, item_id)
);

CREATE TABLE hotel_kitchen_preparations (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  order_id UUID NOT NULL REFERENCES hotel_orders(id),
  order_line_id UUID NOT NULL REFERENCES hotel_order_lines(id),
  request_id UUID NOT NULL,
  quantity NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
  prepared_by UUID NOT NULL REFERENCES users(id),
  prepared_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, request_id, order_line_id)
);

CREATE INDEX ix_hotel_kitchen_queue
  ON hotel_orders (organization_id, branch_id, kitchen_status, created_at);

INSERT INTO permissions (id, code, description, created_at)
VALUES (gen_random_uuid(), 'hotel:kitchen', 'View and prepare hotel kitchen orders', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'hotel:kitchen'
WHERE r.role_key = 'KITCHEN'
ON CONFLICT DO NOTHING;
