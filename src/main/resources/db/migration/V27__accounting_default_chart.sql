INSERT INTO accounts (
  id,
  organization_id,
  parent_account_id,
  account_code,
  account_name,
  account_type,
  active,
  created_at,
  updated_at,
  version
)
VALUES
  ('00000000-0000-4000-8000-000000000701', '00000000-0000-4000-8000-000000000001', NULL, '1000', 'Cash on Hand', 'ASSET', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000702', '00000000-0000-4000-8000-000000000001', NULL, '1010', 'Bank Account', 'ASSET', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000703', '00000000-0000-4000-8000-000000000001', NULL, '1020', 'M-Pesa Wallet', 'ASSET', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000704', '00000000-0000-4000-8000-000000000001', NULL, '1030', 'Petty Cash', 'ASSET', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000705', '00000000-0000-4000-8000-000000000001', NULL, '1100', 'Accounts Receivable', 'ASSET', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000706', '00000000-0000-4000-8000-000000000001', NULL, '1200', 'Inventory', 'ASSET', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000707', '00000000-0000-4000-8000-000000000001', NULL, '2000', 'Accounts Payable', 'LIABILITY', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000708', '00000000-0000-4000-8000-000000000001', NULL, '2100', 'VAT Payable', 'LIABILITY', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000709', '00000000-0000-4000-8000-000000000001', NULL, '3000', 'Owner Equity', 'EQUITY', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000710', '00000000-0000-4000-8000-000000000001', NULL, '4000', 'Sales Revenue', 'REVENUE', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000711', '00000000-0000-4000-8000-000000000001', NULL, '5000', 'Cost of Goods Sold', 'COGS', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000712', '00000000-0000-4000-8000-000000000001', NULL, '6000', 'Operating Expenses', 'EXPENSE', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000713', '00000000-0000-4000-8000-000000000001', NULL, '6100', 'Payroll Expense', 'EXPENSE', TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000714', '00000000-0000-4000-8000-000000000001', NULL, '6200', 'Rent Expense', 'EXPENSE', TRUE, now(), now(), 0)
ON CONFLICT (organization_id, account_code) DO NOTHING;

INSERT INTO financial_periods (
  id,
  organization_id,
  name,
  period_start,
  period_end,
  status,
  created_at,
  updated_at,
  version
)
SELECT
  '00000000-0000-4000-8000-000000000751',
  '00000000-0000-4000-8000-000000000001',
  'FY ' || extract(year from current_date)::int,
  date_trunc('year', current_date)::date,
  (date_trunc('year', current_date) + interval '1 year - 1 day')::date,
  'OPEN',
  now(),
  now(),
  0
WHERE EXISTS (
  SELECT 1
  FROM organizations
  WHERE id = '00000000-0000-4000-8000-000000000001'
)
ON CONFLICT (organization_id, name) DO NOTHING;

INSERT INTO tax_rules (
  id,
  organization_id,
  tax_code,
  name,
  tax_category,
  rate,
  inclusive,
  effective_from,
  effective_to,
  active,
  created_at,
  updated_at,
  version
)
VALUES
  ('00000000-0000-4000-8000-000000000781', '00000000-0000-4000-8000-000000000001', 'VAT16', 'Standard VAT', 'VAT', 16.0000, FALSE, '2026-01-01', NULL, TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000782', '00000000-0000-4000-8000-000000000001', 'ZERO', 'Zero Rated', 'VAT', 0.0000, FALSE, '2026-01-01', NULL, TRUE, now(), now(), 0),
  ('00000000-0000-4000-8000-000000000783', '00000000-0000-4000-8000-000000000001', 'EXEMPT', 'Exempt Supply', 'VAT', 0.0000, FALSE, '2026-01-01', NULL, TRUE, now(), now(), 0)
ON CONFLICT (organization_id, tax_code, effective_from) DO NOTHING;
