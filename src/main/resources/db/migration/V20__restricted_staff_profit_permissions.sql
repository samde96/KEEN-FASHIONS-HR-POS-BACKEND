INSERT INTO permissions (id, code, description, created_at)
VALUES
  ('00000000-0000-4000-8000-000000000530', 'profit:view', 'View buying costs, gross profit, contribution, and profit reports', now())
ON CONFLICT (code) DO UPDATE
SET description = excluded.description;

WITH role_permission_catalog(role_key, permission_code) AS (
  VALUES
    ('ADMIN', 'profit:view'),
    ('AUDITOR', 'profit:view'),
    ('OWNER', 'profit:view'),
    ('STORE_MANAGER', 'profit:view'),
    ('SUPERVISOR', 'profit:view')
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

DELETE FROM role_permissions role_permission
USING roles role, permissions permission
WHERE role_permission.role_id = role.id
  AND role_permission.permission_id = permission.id
  AND role.organization_id = '00000000-0000-4000-8000-000000000001'
  AND role.role_key = 'SHOP_MANAGER'
  AND permission.code IN ('reports:view', 'transfer:receive');
