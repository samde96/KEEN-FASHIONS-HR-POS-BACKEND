CREATE TABLE expenses (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  expense_number VARCHAR(40) NOT NULL,
  category VARCHAR(80) NOT NULL,
  description VARCHAR(180) NOT NULL,
  vendor_name VARCHAR(160),
  amount NUMERIC(14, 2) NOT NULL,
  payment_method VARCHAR(32) NOT NULL,
  payment_reference VARCHAR(80),
  status VARCHAR(32) NOT NULL,
  notes VARCHAR(500),
  incurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_expenses_amount_positive CHECK (amount > 0),
  CONSTRAINT ck_expenses_payment_method CHECK (
    payment_method IN ('MPESA', 'CASH', 'CARD', 'BANK_TRANSFER')
  ),
  CONSTRAINT ck_expenses_status CHECK (status IN ('PENDING', 'PAID', 'VOID')),
  CONSTRAINT ux_expenses_organization_expense_number UNIQUE (organization_id, expense_number)
);

CREATE INDEX ix_expenses_organization_incurred
  ON expenses (organization_id, incurred_at DESC);

CREATE INDEX ix_expenses_branch_incurred
  ON expenses (branch_id, incurred_at DESC);
