CREATE TABLE leave_types (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  name VARCHAR(120) NOT NULL,
  code VARCHAR(40) NOT NULL,
  description VARCHAR(500),
  requires_balance BOOLEAN NOT NULL DEFAULT TRUE,
  default_days INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_leave_types_organization_code UNIQUE (organization_id, code),
  CONSTRAINT ck_leave_types_default_days CHECK (default_days >= 0)
);

CREATE TABLE leave_balances (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  employee_id UUID NOT NULL REFERENCES employees(id),
  leave_type_id UUID NOT NULL REFERENCES leave_types(id),
  balance_days NUMERIC(8,2) NOT NULL DEFAULT 0,
  used_days NUMERIC(8,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_leave_balances_employee_type UNIQUE (employee_id, leave_type_id),
  CONSTRAINT ck_leave_balances_balance_days CHECK (balance_days >= 0),
  CONSTRAINT ck_leave_balances_used_days CHECK (used_days >= 0)
);

CREATE TABLE leave_requests (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  employee_id UUID NOT NULL REFERENCES employees(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  leave_type_id UUID NOT NULL REFERENCES leave_types(id),
  approver_user_id UUID REFERENCES users(id),
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  requested_days NUMERIC(8,2) NOT NULL,
  reason VARCHAR(2000),
  approver_comments VARCHAR(1000),
  status VARCHAR(32) NOT NULL,
  submitted_at TIMESTAMPTZ,
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_leave_requests_requested_days CHECK (requested_days > 0),
  CONSTRAINT ck_leave_requests_status CHECK (
    status IN ('DRAFT', 'SUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED')
  ),
  CONSTRAINT ck_leave_requests_date_range CHECK (end_date >= start_date)
);

CREATE INDEX ix_leave_types_organization_active
  ON leave_types (organization_id, active, name);

CREATE INDEX ix_leave_balances_employee
  ON leave_balances (employee_id);

CREATE INDEX ix_leave_requests_organization_dates
  ON leave_requests (organization_id, start_date DESC, end_date DESC);

CREATE INDEX ix_leave_requests_branch_status
  ON leave_requests (branch_id, status);

INSERT INTO permissions (id, code, description, created_at)
VALUES
  ('00000000-0000-4000-8000-000000000520', 'hr:leave:view', 'View HR leave requests and balances', now()),
  ('00000000-0000-4000-8000-000000000521', 'hr:leave:request', 'Create and submit leave requests', now()),
  ('00000000-0000-4000-8000-000000000522', 'hr:leave:approve', 'Approve or reject leave requests', now()),
  ('00000000-0000-4000-8000-000000000523', 'hr:leave:manage', 'Manage leave types and balances', now())
ON CONFLICT (code) DO UPDATE
SET description = excluded.description;

WITH role_permission_catalog(role_key, permission_code) AS (
  VALUES
    ('ADMIN', 'hr:leave:view'),
    ('ADMIN', 'hr:leave:request'),
    ('ADMIN', 'hr:leave:approve'),
    ('ADMIN', 'hr:leave:manage'),
    ('OWNER', 'hr:leave:view'),
    ('OWNER', 'hr:leave:request'),
    ('OWNER', 'hr:leave:approve'),
    ('OWNER', 'hr:leave:manage'),
    ('SHOP_MANAGER', 'hr:leave:view'),
    ('SHOP_MANAGER', 'hr:leave:request')
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
