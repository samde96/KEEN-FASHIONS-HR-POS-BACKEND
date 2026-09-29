CREATE TABLE payroll_sales_bonus_rules (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  daily_sales_target NUMERIC(14,2) NOT NULL DEFAULT 0,
  bonus_per_target_day NUMERIC(14,2) NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_payroll_sales_bonus_rules_organization UNIQUE (organization_id),
  CONSTRAINT ck_payroll_sales_bonus_rule_amounts_non_negative CHECK (
    daily_sales_target >= 0
    AND bonus_per_target_day >= 0
  )
);

ALTER TABLE payroll_employees
  ADD COLUMN automatic_bonus_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN manual_bonus_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN qualifying_sales_days INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN sales_bonus_target NUMERIC(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN sales_bonus_per_day NUMERIC(14,2) NOT NULL DEFAULT 0,
  ADD CONSTRAINT ck_payroll_employee_sales_bonus_snapshot_non_negative CHECK (
    automatic_bonus_amount >= 0
    AND manual_bonus_amount >= 0
    AND qualifying_sales_days >= 0
    AND sales_bonus_target >= 0
    AND sales_bonus_per_day >= 0
  );

UPDATE payroll_employees
SET manual_bonus_amount = bonus_amount
WHERE bonus_amount > 0;
