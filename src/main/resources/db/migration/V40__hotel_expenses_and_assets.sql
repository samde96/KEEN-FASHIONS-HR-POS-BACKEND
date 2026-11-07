CREATE TABLE hotel_expenses (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  expense_date DATE NOT NULL,
  category VARCHAR(32) NOT NULL CHECK (
    category IN ('FOOD_SUPPLIES', 'UTILITIES', 'RENT', 'REPAIRS_MAINTENANCE', 'PAYROLL', 'TRANSPORT', 'OTHER')
  ),
  description VARCHAR(160) NOT NULL,
  vendor VARCHAR(160) NOT NULL DEFAULT '',
  amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  payment_method VARCHAR(16) NOT NULL CHECK (payment_method IN ('CASH', 'MPESA', 'CARD', 'BANK', 'OTHER')),
  reference VARCHAR(100) NOT NULL DEFAULT '',
  notes VARCHAR(1000) NOT NULL DEFAULT '',
  created_by UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, id)
);

CREATE INDEX hotel_expenses_branch_date_idx
  ON hotel_expenses (organization_id, branch_id, expense_date DESC, created_at DESC);

CREATE TABLE hotel_assets (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  name VARCHAR(160) NOT NULL,
  category VARCHAR(16) NOT NULL CHECK (category IN ('EQUIPMENT', 'FURNITURE', 'ELECTRONICS', 'OTHER')),
  quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 100000),
  purchase_date DATE NOT NULL,
  purchase_cost NUMERIC(14,2) NOT NULL CHECK (purchase_cost >= 0),
  location VARCHAR(160) NOT NULL DEFAULT '',
  status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'NEEDS_REPAIR', 'RETIRED')),
  notes VARCHAR(1000) NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, id)
);

CREATE INDEX hotel_assets_branch_status_idx
  ON hotel_assets (organization_id, branch_id, status, name);
