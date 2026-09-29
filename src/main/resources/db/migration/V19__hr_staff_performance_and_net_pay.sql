ALTER TABLE employees
  ADD COLUMN basic_salary NUMERIC(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN salary_payment_method VARCHAR(16) NOT NULL DEFAULT 'UNSPECIFIED',
  ADD COLUMN bank_name VARCHAR(120),
  ADD COLUMN bank_account_number VARCHAR(80),
  ADD COLUMN bank_account_name VARCHAR(160),
  ADD COLUMN mpesa_number VARCHAR(40),
  ADD CONSTRAINT ck_employees_basic_salary_non_negative CHECK (basic_salary >= 0),
  ADD CONSTRAINT ck_employees_salary_payment_method CHECK (
    salary_payment_method IN ('UNSPECIFIED', 'BANK', 'MPESA')
  );

ALTER TABLE sales
  ADD COLUMN sold_by_employee_id UUID REFERENCES employees(id);

CREATE INDEX ix_sales_sold_by_employee_sold_at
  ON sales (sold_by_employee_id, sold_at DESC);

CREATE TABLE employee_performance_entries (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  employee_id UUID NOT NULL REFERENCES employees(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  created_by_user_id UUID REFERENCES users(id),
  entry_type VARCHAR(32) NOT NULL,
  entry_date DATE NOT NULL,
  title VARCHAR(160) NOT NULL,
  amount NUMERIC(14,2),
  score INTEGER,
  notes VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_employee_performance_entry_type CHECK (
    entry_type IN ('PERFORMANCE_REVIEW', 'BONUS', 'LOSS', 'MANUAL_SALE')
  ),
  CONSTRAINT ck_employee_performance_amount_non_negative CHECK (
    amount IS NULL OR amount >= 0
  ),
  CONSTRAINT ck_employee_performance_score_range CHECK (
    score IS NULL OR (score >= 0 AND score <= 100)
  ),
  CONSTRAINT ck_employee_performance_entry_value CHECK (
    (entry_type = 'PERFORMANCE_REVIEW' AND score IS NOT NULL AND amount IS NULL)
    OR (entry_type <> 'PERFORMANCE_REVIEW' AND amount IS NOT NULL AND amount > 0 AND score IS NULL)
  )
);

CREATE INDEX ix_employee_performance_entries_organization_date
  ON employee_performance_entries (organization_id, entry_date DESC);

CREATE INDEX ix_employee_performance_entries_employee_date
  ON employee_performance_entries (employee_id, entry_date DESC);

CREATE INDEX ix_employee_performance_entries_branch_date
  ON employee_performance_entries (branch_id, entry_date DESC);

ALTER TABLE payroll_employees
  ADD COLUMN basic_salary NUMERIC(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN bonus_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN loss_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN sales_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN performance_score INTEGER,
  ADD COLUMN payment_method VARCHAR(16) NOT NULL DEFAULT 'UNSPECIFIED',
  ADD COLUMN payment_destination VARCHAR(220),
  ADD CONSTRAINT ck_payroll_employee_component_totals_non_negative CHECK (
    basic_salary >= 0
    AND bonus_amount >= 0
    AND loss_amount >= 0
    AND sales_amount >= 0
  ),
  ADD CONSTRAINT ck_payroll_employee_performance_score_range CHECK (
    performance_score IS NULL OR (performance_score >= 0 AND performance_score <= 100)
  ),
  ADD CONSTRAINT ck_payroll_employee_payment_method CHECK (
    payment_method IN ('UNSPECIFIED', 'BANK', 'MPESA')
  );

INSERT INTO permissions (id, code, description, created_at)
VALUES
  ('00000000-0000-4000-8000-000000000528', 'hr:performance:view', 'View staff performance, sales, bonuses, losses, and pay projections', now()),
  ('00000000-0000-4000-8000-000000000529', 'hr:performance:manage', 'Record staff performance reviews, bonuses, losses, and manual sales', now())
ON CONFLICT (code) DO UPDATE
SET description = excluded.description;

WITH role_permission_catalog(role_key, permission_code) AS (
  VALUES
    ('ADMIN', 'hr:performance:view'),
    ('ADMIN', 'hr:performance:manage'),
    ('OWNER', 'hr:performance:view'),
    ('OWNER', 'hr:performance:manage'),
    ('SHOP_MANAGER', 'hr:performance:view')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM role_permission_catalog catalog
JOIN roles role
  ON role.organization_id = '00000000-0000-4000-8000-000000000001'
  AND role.role_key = catalog.role_key
JOIN permissions permission
  ON permission.code = catalog.permission_code
ON CONFLICT DO NOTHING;
