DELETE FROM role_permissions role_permission
USING roles role, permissions permission
WHERE role_permission.role_id = role.id
  AND role_permission.permission_id = permission.id
  AND role.role_key <> 'ADMIN'
  AND permission.code LIKE 'hr:%';
