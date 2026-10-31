DELETE FROM role_permissions role_permission
USING permissions permission
WHERE role_permission.permission_id = permission.id
  AND (
    permission.code LIKE 'hr:%'
    OR permission.code IN ('etims:manage', 'transfer:create', 'transfer:receive')
  );

DELETE FROM permissions
WHERE code LIKE 'hr:%'
   OR code IN ('etims:manage', 'transfer:create', 'transfer:receive');

UPDATE roles
SET description = 'Shop operations, sales review, and reporting'
WHERE role_key = 'SHOP_MANAGER';

UPDATE roles
SET description = 'Inventory adjustments and reports'
WHERE role_key = 'STORE_MANAGER';

ALTER TABLE sales
  DROP COLUMN IF EXISTS sold_by_employee_id;

DROP TABLE IF EXISTS stock_transfers CASCADE;

DROP TABLE IF EXISTS employee_documents CASCADE;
DROP TABLE IF EXISTS payroll_sales_bonus_rules CASCADE;
DROP TABLE IF EXISTS payroll_employee_components CASCADE;
DROP TABLE IF EXISTS payroll_employees CASCADE;
DROP TABLE IF EXISTS payroll_runs CASCADE;
DROP TABLE IF EXISTS payroll_periods CASCADE;
DROP TABLE IF EXISTS payroll_components CASCADE;
DROP TABLE IF EXISTS employee_performance_entries CASCADE;
DROP TABLE IF EXISTS leave_requests CASCADE;
DROP TABLE IF EXISTS leave_balances CASCADE;
DROP TABLE IF EXISTS leave_types CASCADE;
DROP TABLE IF EXISTS attendance_records CASCADE;
DROP TABLE IF EXISTS employees CASCADE;
DROP TABLE IF EXISTS job_titles CASCADE;
DROP TABLE IF EXISTS departments CASCADE;

DROP TABLE IF EXISTS etims_submission_history CASCADE;
DROP TABLE IF EXISTS etims_submission_queue CASCADE;
DROP TABLE IF EXISTS etims_configurations CASCADE;

ALTER TABLE products
  DROP COLUMN IF EXISTS etims_item_code,
  DROP COLUMN IF EXISTS etims_unit_of_measure,
  DROP COLUMN IF EXISTS etims_tax_rate;

ALTER TABLE invoices
  DROP CONSTRAINT IF EXISTS ck_invoices_etims_status,
  DROP COLUMN IF EXISTS etims_status,
  DROP COLUMN IF EXISTS etims_reference;
