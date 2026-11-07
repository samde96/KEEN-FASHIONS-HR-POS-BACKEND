CREATE TABLE hr_bonus_rules (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  effective_from DATE NOT NULL,
  daily_target NUMERIC(14,2) NOT NULL CHECK (daily_target >= 0),
  bonus_mode VARCHAR(30) NOT NULL CHECK (bonus_mode IN ('FIXED', 'PERCENT_EXCESS')),
  bonus_value NUMERIC(14,2) NOT NULL CHECK (bonus_value >= 0),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (branch_id, effective_from)
);

CREATE TABLE hr_payroll_adjustments (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  request_key UUID NOT NULL UNIQUE,
  employee_id UUID NOT NULL REFERENCES employees(id),
  effective_date DATE NOT NULL,
  kind VARCHAR(30) NOT NULL CHECK (kind IN ('MANUAL_BONUS', 'AUTO_BONUS_OVERRIDE', 'DEDUCTION')),
  amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
  reason VARCHAR(500) NOT NULL,
  changed_by_user_id UUID REFERENCES users(id),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE hr_payroll_losses (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  request_key UUID NOT NULL UNIQUE,
  source_key VARCHAR(180) UNIQUE,
  branch_id UUID NOT NULL REFERENCES branches(id),
  occurred_on DATE NOT NULL,
  deduction_date DATE NOT NULL,
  amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  recoverable_amount NUMERIC(14,2) NOT NULL CHECK (recoverable_amount >= 0 AND recoverable_amount <= amount),
  allocation_mode VARCHAR(30) NOT NULL CHECK (allocation_mode IN ('ALL_BRANCH', 'SELECTED', 'CUSTOM', 'WAIVED')),
  reason VARCHAR(500) NOT NULL,
  changed_by_user_id UUID REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE hr_payroll_loss_allocations (
  id UUID PRIMARY KEY,
  loss_id UUID NOT NULL REFERENCES hr_payroll_losses(id) ON DELETE CASCADE,
  employee_id UUID NOT NULL REFERENCES employees(id),
  amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (loss_id, employee_id)
);

CREATE TABLE hr_payroll_runs (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  period DATE NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('APPROVED', 'PAID', 'REOPENED')),
  snapshot TEXT NOT NULL,
  approved_by_user_id UUID REFERENCES users(id),
  approved_at TIMESTAMPTZ NOT NULL,
  paid_at TIMESTAMPTZ,
  payment_reference VARCHAR(180),
  reopen_reason VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (organization_id, period)
);

CREATE TABLE hr_payroll_payments (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  run_id UUID NOT NULL REFERENCES hr_payroll_runs(id),
  period DATE NOT NULL,
  snapshot TEXT NOT NULL,
  payment_reference VARCHAR(180) NOT NULL,
  recorded_by_user_id UUID REFERENCES users(id),
  paid_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE workforce_records (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  request_key UUID NOT NULL UNIQUE,
  user_id UUID NOT NULL REFERENCES users(id),
  branch_id UUID REFERENCES branches(id),
  kind VARCHAR(30) NOT NULL CHECK (kind IN ('WORK', 'AVAILABILITY', 'SICK_LEAVE', 'OFF_DAY', 'ANNUAL_LEAVE', 'ABSENCE')),
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ,
  break_minutes INTEGER NOT NULL DEFAULT 0 CHECK (break_minutes >= 0),
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  source VARCHAR(20) NOT NULL CHECK (source IN ('SELF', 'ADMIN', 'CLOCK')),
  note VARCHAR(500) NOT NULL,
  reviewed_by_user_id UUID REFERENCES users(id),
  reviewed_at TIMESTAMPTZ,
  review_note VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (ends_at IS NULL OR ends_at > starts_at),
  CHECK (kind NOT IN ('WORK', 'AVAILABILITY') OR branch_id IS NOT NULL)
);

CREATE TABLE location_operating_hours (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
  opens_at TIME,
  closes_at TIME,
  closed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (branch_id, day_of_week),
  CHECK (closed OR (opens_at IS NOT NULL AND closes_at IS NOT NULL AND closes_at > opens_at))
);

CREATE TABLE payroll_salary_plans (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  employee_id UUID NOT NULL REFERENCES employees(id),
  effective_month DATE NOT NULL,
  monthly_salary NUMERIC(14,2) NOT NULL CHECK (monthly_salary >= 0),
  reason VARCHAR(500) NOT NULL,
  changed_by_user_id UUID REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (employee_id, effective_month)
);

CREATE TABLE payroll_funding_shares (
  plan_id UUID NOT NULL REFERENCES payroll_salary_plans(id) ON DELETE CASCADE,
  branch_id UUID NOT NULL REFERENCES branches(id),
  percentage NUMERIC(7,4) NOT NULL CHECK (percentage > 0 AND percentage <= 100),
  PRIMARY KEY (plan_id, branch_id)
);

CREATE INDEX ix_hr_bonus_rules_branch_date ON hr_bonus_rules (organization_id, branch_id, effective_from DESC);
CREATE INDEX ix_hr_adjustments_employee_date ON hr_payroll_adjustments (organization_id, employee_id, effective_date);
CREATE INDEX ix_hr_losses_deduction_date ON hr_payroll_losses (organization_id, deduction_date);
CREATE INDEX ix_hr_loss_allocations_employee ON hr_payroll_loss_allocations (employee_id);
CREATE INDEX ix_hr_payroll_payments_period ON hr_payroll_payments (organization_id, period DESC, paid_at DESC);
CREATE INDEX ix_workforce_user_period ON workforce_records (organization_id, user_id, starts_at, ends_at);
CREATE INDEX ix_workforce_branch_period ON workforce_records (organization_id, branch_id, starts_at, ends_at);
CREATE INDEX ix_workforce_status_period ON workforce_records (organization_id, status, starts_at);
CREATE INDEX ix_payroll_salary_effective ON payroll_salary_plans (organization_id, employee_id, effective_month DESC);
CREATE UNIQUE INDEX ux_hr_bonus_override_employee_date
  ON hr_payroll_adjustments (employee_id, effective_date)
  WHERE kind='AUTO_BONUS_OVERRIDE' AND active=true;
