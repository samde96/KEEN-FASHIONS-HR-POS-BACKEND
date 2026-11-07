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
  basic_salary NUMERIC(14,2) NOT NULL DEFAULT 0,
  salary_payment_method VARCHAR(16) NOT NULL DEFAULT 'UNSPECIFIED',
  bank_name VARCHAR(120),
  bank_account_number VARCHAR(80),
  bank_account_name VARCHAR(160),
  mpesa_number VARCHAR(40),
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
  ),
  CONSTRAINT ck_employees_basic_salary_non_negative CHECK (basic_salary >= 0),
  CONSTRAINT ck_employees_salary_payment_method CHECK (
    salary_payment_method IN ('UNSPECIFIED', 'BANK', 'MPESA')
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
