ALTER TABLE roles
  ADD COLUMN role_key VARCHAR(80);

UPDATE roles
SET role_key = regexp_replace(
    regexp_replace(upper(trim(name)), '[^A-Z0-9]+', '_', 'g'),
    '(^_+|_+$)',
    '',
    'g'
  )
WHERE role_key IS NULL;

UPDATE roles
SET role_key = 'ROLE_' || replace(id::text, '-', '')
WHERE role_key IS NULL OR role_key = '';

ALTER TABLE roles
  ALTER COLUMN role_key SET NOT NULL;

CREATE UNIQUE INDEX ux_roles_organization_key
  ON roles (organization_id, role_key);

INSERT INTO permissions (id, code, description, created_at)
VALUES
  ('00000000-0000-4000-8000-000000000501', 'admin:manage', 'Manage users, roles, branches, products, and settings', now()),
  ('00000000-0000-4000-8000-000000000502', 'audit:view', 'View audit logs', now()),
  ('00000000-0000-4000-8000-000000000503', 'inventory:adjust', 'Adjust inventory balances', now()),
  ('00000000-0000-4000-8000-000000000504', 'inventory:receive', 'Receive supplier stock', now()),
  ('00000000-0000-4000-8000-000000000505', 'pos:sell', 'Perform POS sale', now()),
  ('00000000-0000-4000-8000-000000000506', 'pos:supervise', 'Approve POS sale corrections, returns, and refunds', now()),
  ('00000000-0000-4000-8000-000000000507', 'reports:view', 'View reports', now()),
  ('00000000-0000-4000-8000-000000000508', 'sales:view', 'View Sales Data', now()),
  ('00000000-0000-4000-8000-000000000509', 'transfer:create', 'Create warehouse to shop transfer', now()),
  ('00000000-0000-4000-8000-000000000510', 'transfer:receive', 'Receive shop transfer', now())
ON CONFLICT (code) DO UPDATE
SET description = excluded.description;

INSERT INTO roles (id, organization_id, name, role_key, description, created_at, updated_at, version)
VALUES
  ('00000000-0000-4000-8000-000000000401', '00000000-0000-4000-8000-000000000001', 'Admin', 'ADMIN', 'Full system administration for this business', now(), now(), 0),
  ('00000000-0000-4000-8000-000000000402', '00000000-0000-4000-8000-000000000001', 'Auditor', 'AUDITOR', 'Audit review and reporting access', now(), now(), 0),
  ('00000000-0000-4000-8000-000000000403', '00000000-0000-4000-8000-000000000001', 'Cashier', 'CASHIER', 'POS sales and sales lookup access', now(), now(), 0),
  ('00000000-0000-4000-8000-000000000404', '00000000-0000-4000-8000-000000000001', 'Owner', 'OWNER', 'Business owner access across operations', now(), now(), 0),
  ('00000000-0000-4000-8000-000000000405', '00000000-0000-4000-8000-000000000001', 'Shop Manager', 'SHOP_MANAGER', 'Shop operations, sales review, and transfers', now(), now(), 0),
  ('00000000-0000-4000-8000-000000000406', '00000000-0000-4000-8000-000000000001', 'Store Manager', 'STORE_MANAGER', 'Inventory adjustments, reports, and transfer creation', now(), now(), 0),
  ('00000000-0000-4000-8000-000000000407', '00000000-0000-4000-8000-000000000001', 'Supervisor', 'SUPERVISOR', 'POS supervision and sales review', now(), now(), 0)
ON CONFLICT (organization_id, role_key) DO UPDATE
SET name = excluded.name,
    description = excluded.description,
    updated_at = now();

WITH role_permission_catalog(role_key, permission_code) AS (
  VALUES
    ('ADMIN', 'admin:manage'),
    ('ADMIN', 'audit:view'),
    ('ADMIN', 'inventory:adjust'),
    ('ADMIN', 'inventory:receive'),
    ('ADMIN', 'pos:sell'),
    ('ADMIN', 'pos:supervise'),
    ('ADMIN', 'reports:view'),
    ('ADMIN', 'sales:view'),
    ('ADMIN', 'transfer:create'),
    ('ADMIN', 'transfer:receive'),
    ('AUDITOR', 'audit:view'),
    ('AUDITOR', 'reports:view'),
    ('CASHIER', 'pos:sell'),
    ('CASHIER', 'sales:view'),
    ('OWNER', 'admin:manage'),
    ('OWNER', 'audit:view'),
    ('OWNER', 'inventory:adjust'),
    ('OWNER', 'inventory:receive'),
    ('OWNER', 'pos:sell'),
    ('OWNER', 'pos:supervise'),
    ('OWNER', 'reports:view'),
    ('OWNER', 'sales:view'),
    ('OWNER', 'transfer:create'),
    ('OWNER', 'transfer:receive'),
    ('SHOP_MANAGER', 'pos:sell'),
    ('SHOP_MANAGER', 'pos:supervise'),
    ('SHOP_MANAGER', 'reports:view'),
    ('SHOP_MANAGER', 'sales:view'),
    ('SHOP_MANAGER', 'transfer:receive'),
    ('STORE_MANAGER', 'inventory:adjust'),
    ('STORE_MANAGER', 'reports:view'),
    ('STORE_MANAGER', 'transfer:create'),
    ('SUPERVISOR', 'pos:sell'),
    ('SUPERVISOR', 'pos:supervise'),
    ('SUPERVISOR', 'reports:view'),
    ('SUPERVISOR', 'sales:view')
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
