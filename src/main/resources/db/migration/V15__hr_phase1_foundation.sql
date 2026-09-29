CREATE TABLE departments (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID REFERENCES branches(id),
  name VARCHAR(120) NOT NULL,
  code VARCHAR(40) NOT NULL,
  description VARCHAR(500),
  manager_user_id UUID REFERENCES users(id),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_departments_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE job_titles (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  department_id UUID NOT NULL REFERENCES departments(id),
  title VARCHAR(120) NOT NULL,
  code VARCHAR(40) NOT NULL,
  description VARCHAR(500),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_job_titles_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE employees (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  user_id UUID REFERENCES users(id),
  primary_branch_id UUID NOT NULL REFERENCES branches(id),
  department_id UUID REFERENCES departments(id),
  job_title_id UUID REFERENCES job_titles(id),
  employee_number VARCHAR(40) NOT NULL,
  first_name VARCHAR(80) NOT NULL,
  middle_name VARCHAR(80),
  last_name VARCHAR(80) NOT NULL,
  preferred_name VARCHAR(80),
  email VARCHAR(254),
  phone VARCHAR(40),
  gender VARCHAR(32),
  date_of_birth DATE,
  national_id_number VARCHAR(80),
  employment_type VARCHAR(32) NOT NULL,
  employment_status VARCHAR(32) NOT NULL,
  joining_date DATE NOT NULL,
  probation_end_date DATE,
  contract_start_date DATE,
  contract_end_date DATE,
  manager_employee_id UUID REFERENCES employees(id),
  work_location VARCHAR(160),
  emergency_contact_name VARCHAR(120),
  emergency_contact_phone VARCHAR(40),
  address VARCHAR(500),
  notes VARCHAR(2000),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_employees_organization_employee_number UNIQUE (organization_id, employee_number),
  CONSTRAINT ux_employees_user_id UNIQUE (user_id),
  CONSTRAINT ck_employees_employment_type CHECK (
    employment_type IN ('PERMANENT', 'CONTRACT', 'TEMPORARY', 'INTERN', 'CASUAL')
  ),
  CONSTRAINT ck_employees_employment_status CHECK (
    employment_status IN ('ACTIVE', 'PROBATION', 'SUSPENDED', 'TERMINATED', 'RESIGNED', 'RETIRED')
  )
);

CREATE INDEX ix_departments_organization_active
  ON departments (organization_id, active, name);

CREATE INDEX ix_departments_branch
  ON departments (branch_id);

CREATE INDEX ix_job_titles_organization_active
  ON job_titles (organization_id, active, title);

CREATE INDEX ix_job_titles_department
  ON job_titles (department_id);

CREATE INDEX ix_employees_organization_active
  ON employees (organization_id, active, last_name, first_name);

CREATE INDEX ix_employees_branch
  ON employees (primary_branch_id);

CREATE INDEX ix_employees_department
  ON employees (department_id);

CREATE INDEX ix_employees_job_title
  ON employees (job_title_id);

CREATE INDEX ix_employees_manager
  ON employees (manager_employee_id);

INSERT INTO permissions (id, code, description, created_at)
VALUES
  ('00000000-0000-4000-8000-000000000511', 'hr:dashboard:view', 'View HR dashboard metrics', now()),
  ('00000000-0000-4000-8000-000000000512', 'hr:employee:view', 'View employee records and profiles', now()),
  ('00000000-0000-4000-8000-000000000513', 'hr:employee:create', 'Create employee records', now()),
  ('00000000-0000-4000-8000-000000000514', 'hr:employee:edit', 'Edit employee records', now()),
  ('00000000-0000-4000-8000-000000000515', 'hr:employee:manage-status', 'Manage employee active and employment status changes', now()),
  ('00000000-0000-4000-8000-000000000516', 'hr:settings:manage', 'Manage HR departments, job titles, and foundational settings', now())
ON CONFLICT (code) DO UPDATE
SET description = excluded.description;

WITH role_permission_catalog(role_key, permission_code) AS (
  VALUES
    ('ADMIN', 'hr:dashboard:view'),
    ('ADMIN', 'hr:employee:view'),
    ('ADMIN', 'hr:employee:create'),
    ('ADMIN', 'hr:employee:edit'),
    ('ADMIN', 'hr:employee:manage-status'),
    ('ADMIN', 'hr:settings:manage'),
    ('OWNER', 'hr:dashboard:view'),
    ('OWNER', 'hr:employee:view'),
    ('OWNER', 'hr:employee:create'),
    ('OWNER', 'hr:employee:edit'),
    ('OWNER', 'hr:employee:manage-status'),
    ('OWNER', 'hr:settings:manage'),
    ('SHOP_MANAGER', 'hr:dashboard:view'),
    ('SHOP_MANAGER', 'hr:employee:view')
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
