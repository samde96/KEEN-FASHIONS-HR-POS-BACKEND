CREATE TABLE attendance_records (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  employee_id UUID NOT NULL REFERENCES employees(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  attendance_date DATE NOT NULL,
  clock_in TIMESTAMPTZ,
  clock_out TIMESTAMPTZ,
  status VARCHAR(32) NOT NULL,
  worked_minutes INTEGER NOT NULL DEFAULT 0,
  overtime_minutes INTEGER NOT NULL DEFAULT 0,
  late_minutes INTEGER NOT NULL DEFAULT 0,
  notes VARCHAR(1000),
  correction_reason VARCHAR(500),
  corrected BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_attendance_records_employee_date UNIQUE (employee_id, attendance_date),
  CONSTRAINT ck_attendance_records_status CHECK (
    status IN ('PRESENT', 'ABSENT', 'LATE', 'HALF_DAY', 'ON_LEAVE', 'HOLIDAY', 'OFF_DAY')
  ),
  CONSTRAINT ck_attendance_records_worked_minutes CHECK (worked_minutes >= 0),
  CONSTRAINT ck_attendance_records_overtime_minutes CHECK (overtime_minutes >= 0),
  CONSTRAINT ck_attendance_records_late_minutes CHECK (late_minutes >= 0)
);

CREATE INDEX ix_attendance_records_organization_date
  ON attendance_records (organization_id, attendance_date DESC);

CREATE INDEX ix_attendance_records_branch_date
  ON attendance_records (branch_id, attendance_date DESC);

CREATE INDEX ix_attendance_records_employee_date
  ON attendance_records (employee_id, attendance_date DESC);

INSERT INTO permissions (id, code, description, created_at)
VALUES
  ('00000000-0000-4000-8000-000000000517', 'hr:attendance:view', 'View HR attendance records and summaries', now()),
  ('00000000-0000-4000-8000-000000000518', 'hr:attendance:manage', 'Create and update HR attendance records', now()),
  ('00000000-0000-4000-8000-000000000519', 'hr:attendance:correct', 'Correct attendance records with audit trail', now())
ON CONFLICT (code) DO UPDATE
SET description = excluded.description;

WITH role_permission_catalog(role_key, permission_code) AS (
  VALUES
    ('ADMIN', 'hr:attendance:view'),
    ('ADMIN', 'hr:attendance:manage'),
    ('ADMIN', 'hr:attendance:correct'),
    ('OWNER', 'hr:attendance:view'),
    ('OWNER', 'hr:attendance:manage'),
    ('OWNER', 'hr:attendance:correct'),
    ('SHOP_MANAGER', 'hr:attendance:view')
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
