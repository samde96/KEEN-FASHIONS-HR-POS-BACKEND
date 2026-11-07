INSERT INTO roles(id, organization_id, name, role_key, description)
SELECT gen_random_uuid(), org.id, 'Waiter', 'WAITER',
       'Hotel restaurant table service and orders'
FROM organizations org
ON CONFLICT (organization_id, role_key) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'hotel:sell'
WHERE r.role_key = 'WAITER'
ON CONFLICT DO NOTHING;
