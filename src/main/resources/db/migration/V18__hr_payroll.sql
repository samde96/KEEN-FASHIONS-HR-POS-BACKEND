CREATE TABLE payroll_components (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  name VARCHAR(120) NOT NULL,
  code VARCHAR(40) NOT NULL,
  component_type VARCHAR(32) NOT NULL,
  taxable BOOLEAN NOT NULL DEFAULT FALSE,
  default_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_payroll_components_organization_code UNIQUE (organization_id, code),
  CONSTRAINT ck_payroll_component_type CHECK (component_type IN ('EARNING', 'DEDUCTION')),
  CONSTRAINT ck_payroll_component_default_amount CHECK (default_amount >= 0)
);

CREATE TABLE payroll_periods (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID REFERENCES branches(id),
  name VARCHAR(120) NOT NULL,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  payment_date DATE,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_payroll_period_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
  CONSTRAINT ck_payroll_period_range CHECK (period_end >= period_start)
);

CREATE TABLE payroll_runs (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  payroll_period_id UUID NOT NULL REFERENCES payroll_periods(id),
  branch_id UUID REFERENCES branches(id),
  name VARCHAR(120) NOT NULL,
  status VARCHAR(32) NOT NULL,
  processed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_payroll_run_status CHECK (
    status IN ('DRAFT', 'CALCULATED', 'REVIEWED', 'APPROVED', 'PROCESSED', 'LOCKED')
  )
);

CREATE TABLE payroll_employees (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  payroll_run_id UUID NOT NULL REFERENCES payroll_runs(id),
  employee_id UUID NOT NULL REFERENCES employees(id),
  gross_pay NUMERIC(14,2) NOT NULL DEFAULT 0,
  total_deductions NUMERIC(14,2) NOT NULL DEFAULT 0,
  net_pay NUMERIC(14,2) NOT NULL DEFAULT 0,
  notes VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_payroll_employees_run_employee UNIQUE (payroll_run_id, employee_id)
);

CREATE TABLE payroll_employee_components (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  payroll_employee_id UUID NOT NULL REFERENCES payroll_employees(id),
  payroll_component_id UUID NOT NULL REFERENCES payroll_components(id),
  amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_payroll_employee_component_amount CHECK (amount >= 0)
);

CREATE INDEX ix_payroll_components_organization_active
  ON payroll_components (organization_id, active, name);

CREATE INDEX ix_payroll_periods_organization_dates
  ON payroll_periods (organization_id, period_start DESC, period_end DESC);

CREATE INDEX ix_payroll_runs_period
  ON payroll_runs (payroll_period_id, status);

CREATE INDEX ix_payroll_employees_run
  ON payroll_employees (payroll_run_id);

INSERT INTO permissions (id, code, description, created_at)
VALUES
  ('00000000-0000-4000-8000-000000000524', 'hr:payroll:view', 'View payroll periods, runs, and payslips', now()),
  ('00000000-0000-4000-8000-000000000525', 'hr:payroll:process', 'Calculate and process payroll runs', now()),
  ('00000000-0000-4000-8000-000000000526', 'hr:payroll:approve', 'Approve payroll runs before processing', now()),
  ('00000000-0000-4000-8000-000000000527', 'hr:payroll:view-payslip', 'View employee payroll breakdowns and payslips', now())
ON CONFLICT (code) DO UPDATE
SET description = excluded.description;

WITH role_permission_catalog(role_key, permission_code) AS (
  VALUES
    ('ADMIN', 'hr:payroll:view'),
    ('ADMIN', 'hr:payroll:process'),
    ('ADMIN', 'hr:payroll:approve'),
    ('ADMIN', 'hr:payroll:view-payslip'),
    ('OWNER', 'hr:payroll:view'),
    ('OWNER', 'hr:payroll:process'),
    ('OWNER', 'hr:payroll:approve'),
    ('OWNER', 'hr:payroll:view-payslip')
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
