DELETE FROM user_roles user_role
USING roles role
WHERE user_role.role_id = role.id
  AND upper(role.role_key) NOT IN ('ADMIN', 'SHOP_MANAGER');

DELETE FROM role_permissions role_permission
USING roles role
WHERE role_permission.role_id = role.id
  AND upper(role.role_key) NOT IN ('ADMIN', 'SHOP_MANAGER');

DELETE FROM roles
WHERE upper(role_key) NOT IN ('ADMIN', 'SHOP_MANAGER');

WITH role_permission_catalog(role_key, permission_code) AS (
  VALUES
    ('ADMIN', 'admin:manage'),
    ('ADMIN', 'accounting:manage'),
    ('ADMIN', 'accounting:view'),
    ('ADMIN', 'audit:view'),
    ('ADMIN', 'commercial:manage'),
    ('ADMIN', 'crm:manage'),
    ('ADMIN', 'inventory:adjust'),
    ('ADMIN', 'inventory:receive'),
    ('ADMIN', 'notifications:view'),
    ('ADMIN', 'pos:sell'),
    ('ADMIN', 'pos:supervise'),
    ('ADMIN', 'procurement:manage'),
    ('ADMIN', 'profit:view'),
    ('ADMIN', 'reports:view'),
    ('ADMIN', 'sales:view'),
    ('SHOP_MANAGER', 'commercial:manage'),
    ('SHOP_MANAGER', 'crm:manage'),
    ('SHOP_MANAGER', 'inventory:adjust'),
    ('SHOP_MANAGER', 'inventory:receive'),
    ('SHOP_MANAGER', 'notifications:view'),
    ('SHOP_MANAGER', 'pos:sell'),
    ('SHOP_MANAGER', 'pos:supervise'),
    ('SHOP_MANAGER', 'procurement:manage'),
    ('SHOP_MANAGER', 'reports:view'),
    ('SHOP_MANAGER', 'sales:view')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM role_permission_catalog catalog
JOIN roles role
  ON role.role_key = catalog.role_key
JOIN permissions permission
  ON permission.code = catalog.permission_code
ON CONFLICT DO NOTHING;

DELETE FROM role_permissions role_permission
USING roles role, permissions permission
WHERE role_permission.role_id = role.id
  AND role_permission.permission_id = permission.id
  AND role.role_key = 'SHOP_MANAGER'
  AND permission.code IN ('admin:manage', 'audit:view', 'accounting:manage', 'profit:view');
