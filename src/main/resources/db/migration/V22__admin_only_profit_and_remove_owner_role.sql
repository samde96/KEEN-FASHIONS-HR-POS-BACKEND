DELETE FROM role_permissions role_permission
USING roles role, permissions permission
WHERE role_permission.role_id = role.id
  AND role_permission.permission_id = permission.id
  AND lower(permission.code) = 'profit:view'
  AND upper(role.role_key) <> 'ADMIN';

DELETE FROM user_roles user_role
USING roles role
WHERE user_role.role_id = role.id
  AND upper(role.role_key) = 'OWNER';

DELETE FROM role_permissions role_permission
USING roles role
WHERE role_permission.role_id = role.id
  AND upper(role.role_key) = 'OWNER';

DELETE FROM roles
WHERE upper(role_key) = 'OWNER';

DELETE FROM user_branch_assignments branch_assignment
USING users user_account
WHERE branch_assignment.user_id = user_account.id
  AND lower(user_account.email) IN ('owner@keen.local', 'owner@external.local');

DELETE FROM user_roles user_role
USING users user_account
WHERE user_role.user_id = user_account.id
  AND lower(user_account.email) IN ('owner@keen.local', 'owner@external.local');

UPDATE users
SET status = 'DISABLED',
    updated_at = now()
WHERE lower(email) IN ('owner@keen.local', 'owner@external.local');
