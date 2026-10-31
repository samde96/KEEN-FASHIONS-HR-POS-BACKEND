INSERT INTO permissions (id, code, description, created_at)
VALUES
  (gen_random_uuid(), 'expenses:create', 'Register own shop expenses', now()),
  (gen_random_uuid(), 'shifts:manage', 'Open and close own shop shifts', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (id, organization_id, name, role_key, description, created_at, updated_at, version)
SELECT gen_random_uuid(), id, 'Cashier', 'CASHIER',
       'Shop sales, own expense registration, and own opening and closing shifts', now(), now(), 0
FROM organizations
ON CONFLICT (organization_id, role_key) DO NOTHING;

DELETE FROM role_permissions rp USING roles r
WHERE rp.role_id = r.id AND r.role_key = 'CASHIER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE (r.role_key = 'CASHIER' AND p.code IN ('pos:sell', 'expenses:create', 'shifts:manage'))
   OR (r.role_key IN ('ADMIN', 'SHOP_MANAGER') AND p.code IN ('expenses:create', 'shifts:manage'))
ON CONFLICT DO NOTHING;

ALTER TABLE expenses ADD COLUMN created_by UUID REFERENCES users(id);
CREATE INDEX ix_expenses_creator_time ON expenses (organization_id, created_by, incurred_at);

CREATE TABLE cashier_shifts (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  cashier_id UUID NOT NULL REFERENCES users(id),
  status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
  opening_float NUMERIC(14,2) NOT NULL CHECK (opening_float >= 0),
  opening_note VARCHAR(1000),
  opened_at TIMESTAMPTZ NOT NULL,
  closed_at TIMESTAMPTZ,
  closing_cash NUMERIC(14,2) CHECK (closing_cash >= 0),
  closing_note VARCHAR(1000),
  cash_sales NUMERIC(14,2),
  cash_expenses NUMERIC(14,2),
  total_sales NUMERIC(14,2),
  transaction_count INTEGER,
  expected_cash NUMERIC(14,2),
  CHECK ((status = 'OPEN' AND closed_at IS NULL AND closing_cash IS NULL)
      OR (status = 'CLOSED' AND closed_at IS NOT NULL AND closing_cash IS NOT NULL
          AND expected_cash IS NOT NULL AND closed_at >= opened_at))
);
CREATE UNIQUE INDEX ux_cashier_one_open_shift
  ON cashier_shifts (organization_id, cashier_id) WHERE status = 'OPEN';
CREATE INDEX ix_cashier_shift_history ON cashier_shifts (organization_id, cashier_id, opened_at DESC);
