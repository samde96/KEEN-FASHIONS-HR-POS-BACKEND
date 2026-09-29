package com.company.fashionpos.staff;

import com.company.fashionpos.branch.BranchRepository;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.organization.OrganizationRepository;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {

  private static final String ADMIN_ROLE_KEY = "ADMIN";
  private static final String OWNER_ROLE_KEY = "OWNER";
  private static final String PROFIT_VIEW_PERMISSION = "profit:view";
  private static final String HR_PERMISSION_PREFIX = "hr:";

  private final UserAccountRepository userAccountRepository;
  private final RoleRepository roleRepository;
  private final BranchRepository branchRepository;
  private final OrganizationRepository organizationRepository;
  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;

  public StaffService(
      UserAccountRepository userAccountRepository,
      RoleRepository roleRepository,
      BranchRepository branchRepository,
      OrganizationRepository organizationRepository,
      JdbcTemplate jdbcTemplate,
      PasswordEncoder passwordEncoder) {
    this.userAccountRepository = userAccountRepository;
    this.roleRepository = roleRepository;
    this.branchRepository = branchRepository;
    this.organizationRepository = organizationRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public List<RoleResponse> listRoles(AuthenticatedUser user) {
    return roleRepository.findWithinOrganization(user.organizationId()).stream()
        .map(this::toRoleResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<PermissionResponse> listPermissions() {
    return jdbcTemplate.query(
        """
        select id, code, description
        from permissions
        order by code
        """,
        this::permissionFromResultSet);
  }

  @Transactional
  public PermissionResponse createPermission(PermissionRequest request) {
    String code = normalizePermissionCode(request.code());
    if (permissionCodeExists(code)) {
      throw new IllegalArgumentException("Permission code is already used");
    }

    UUID permissionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into permissions (id, code, description, created_at)
        values (?, ?, ?, now())
        """,
        permissionId,
        code,
        normalizeOptional(request.description()));
    return requirePermission(permissionId);
  }

  @Transactional
  public PermissionResponse updatePermission(UUID permissionId, PermissionRequest request) {
    requirePermission(permissionId);
    String code = normalizePermissionCode(request.code());
    if (permissionCodeExistsExcludingPermission(code, permissionId)) {
      throw new IllegalArgumentException("Permission code is already used");
    }

    jdbcTemplate.update(
        """
        update permissions
        set code = ?, description = ?
        where id = ?
        """,
        code,
        normalizeOptional(request.description()),
        permissionId);
    return requirePermission(permissionId);
  }

  @Transactional
  public RoleResponse createRole(AuthenticatedUser user, RoleRequest request) {
    String name = normalizeName(request.name());
    String key = normalizeRoleKey(request.key(), name);
    if (roleRepository.existsByNameWithinOrganization(user.organizationId(), name)) {
      throw new IllegalArgumentException("Role name is already used");
    }
    if (roleRepository.existsByKeyWithinOrganization(user.organizationId(), key)) {
      throw new IllegalArgumentException("Role key is already used");
    }
    requireSupportedRoleKey(key);

    Organization organization =
        organizationRepository
            .findById(user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
    Role role =
        new Role(
            UUID.randomUUID(), organization, name, key, normalizeOptional(request.description()));

    Role savedRole = roleRepository.saveAndFlush(role);
    assignRolePermissions(savedRole.getId(), savedRole.getKey(), request.permissionIds());
    return toRoleResponse(savedRole);
  }

  @Transactional
  public RoleResponse updateRole(AuthenticatedUser user, UUID roleId, RoleRequest request) {
    Role role =
        roleRepository
            .findWithinOrganization(roleId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Role not found"));

    String name = normalizeName(request.name());
    String key = normalizeRoleKey(request.key(), name);
    if (roleRepository.existsByNameWithinOrganizationExcludingRole(
        user.organizationId(), name, roleId)) {
      throw new IllegalArgumentException("Role name is already used");
    }
    if (roleRepository.existsByKeyWithinOrganizationExcludingRole(
        user.organizationId(), key, roleId)) {
      throw new IllegalArgumentException("Role key is already used");
    }
    requireSupportedRoleKey(key);

    role.updateDetails(name, key, normalizeOptional(request.description()));
    assignRolePermissions(roleId, key, request.permissionIds());
    return toRoleResponse(role);
  }

  @Transactional
  public void deleteRole(AuthenticatedUser user, UUID roleId) {
    Role role =
        roleRepository
            .findWithinOrganization(roleId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Role not found"));

    if (assignedUserCount(roleId) > 0) {
      throw new IllegalArgumentException("Role is assigned to one or more users");
    }

    roleRepository.delete(role);
  }

  @Transactional(readOnly = true)
  public List<UserResponse> listUsers(AuthenticatedUser user) {
    return userAccountRepository.findWithinOrganization(user.organizationId()).stream()
        .map(this::toUserResponse)
        .toList();
  }

  @Transactional
  public UserResponse createUser(AuthenticatedUser user, UserRequest request) {
    String email = normalizeEmail(request.email());
    if (userAccountRepository.existsByEmailWithinOrganization(user.organizationId(), email)) {
      throw new IllegalArgumentException("User email is already used");
    }

    Organization organization =
        organizationRepository
            .findById(user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
    UserAccount account =
        new UserAccount(
            UUID.randomUUID(), organization, email, normalizeName(request.displayName()));
    account.updateDetails(
        email, normalizeName(request.displayName()), statusOrActive(request.status()));
    account.updatePasswordHash(passwordEncoder.encode(requiredPassword(request.password())));
    UserAccount saved = userAccountRepository.saveAndFlush(account);
    assignRolesAndBranches(user, saved.getId(), request.roleIds(), request.branchIds());
    return toUserResponse(saved);
  }

  @Transactional
  public UserResponse updateUser(AuthenticatedUser user, UUID userId, UserRequest request) {
    UserAccount account =
        userAccountRepository
            .findWithinOrganization(userId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    String email = normalizeEmail(request.email());
    if (userAccountRepository.existsByEmailWithinOrganizationExcludingUser(
        user.organizationId(), email, userId)) {
      throw new IllegalArgumentException("User email is already used");
    }

    account.updateDetails(
        email, normalizeName(request.displayName()), statusOrActive(request.status()));
    if (hasUsablePassword(request.password())) {
      account.updatePasswordHash(passwordEncoder.encode(request.password()));
    }
    assignRolesAndBranches(user, account.getId(), request.roleIds(), request.branchIds());
    return toUserResponse(account);
  }

  @Transactional
  public void disableUser(AuthenticatedUser user, UUID userId) {
    if (user.userId().equals(userId)) {
      throw new IllegalArgumentException("You cannot disable your own user account");
    }

    UserAccount account =
        userAccountRepository
            .findWithinOrganization(userId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
    account.disable();
  }

  private void assignRolesAndBranches(
      AuthenticatedUser user,
      UUID userId,
      Set<UUID> requestedRoleIds,
      Set<UUID> requestedBranchIds) {
    Set<UUID> roleIds = normalizeIds(requestedRoleIds);
    Set<UUID> branchIds = normalizeIds(requestedBranchIds);

    for (UUID roleId : roleIds) {
      if (!roleRepository.existsWithinOrganization(roleId, user.organizationId())) {
        throw new EntityNotFoundException("Role not found");
      }
    }

    for (UUID branchId : branchIds) {
      if (!branchRepository.existsWithinOrganization(branchId, user.organizationId())) {
        throw new EntityNotFoundException("Branch not found");
      }
    }

    jdbcTemplate.update("delete from user_roles where user_id = ?", userId);
    jdbcTemplate.update("delete from user_branch_assignments where user_id = ?", userId);

    batchInsertRoleAssignments(userId, List.copyOf(roleIds));
    batchInsertBranchAssignments(userId, List.copyOf(branchIds));
  }

  private void batchInsertRoleAssignments(UUID userId, List<UUID> roleIds) {
    jdbcTemplate.batchUpdate(
        "insert into user_roles (user_id, role_id) values (?, ?)",
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement statement, int index) throws SQLException {
            statement.setObject(1, userId);
            statement.setObject(2, roleIds.get(index));
          }

          @Override
          public int getBatchSize() {
            return roleIds.size();
          }
        });
  }

  private void batchInsertBranchAssignments(UUID userId, List<UUID> branchIds) {
    jdbcTemplate.batchUpdate(
        "insert into user_branch_assignments (id, user_id, branch_id) values (?, ?, ?)",
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement statement, int index) throws SQLException {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, branchIds.get(index));
          }

          @Override
          public int getBatchSize() {
            return branchIds.size();
          }
        });
  }

  private void assignRolePermissions(
      UUID roleId, String roleKey, Set<UUID> requestedPermissionIds) {
    if (requestedPermissionIds == null) {
      return;
    }

    Set<UUID> permissionIds = normalizeIds(requestedPermissionIds);
    for (UUID permissionId : permissionIds) {
      if (!permissionExists(permissionId)) {
        throw new EntityNotFoundException("Permission not found");
      }
    }
    if (includesProfitViewPermission(permissionIds) && !ADMIN_ROLE_KEY.equals(roleKey)) {
      throw new IllegalArgumentException("Only the Admin role can be assigned profit visibility");
    }
    if (includesHrPermission(permissionIds) && !ADMIN_ROLE_KEY.equals(roleKey)) {
      throw new IllegalArgumentException("Only the Admin role can be assigned HR permissions");
    }

    jdbcTemplate.update("delete from role_permissions where role_id = ?", roleId);
    batchInsertPermissionAssignments(roleId, List.copyOf(permissionIds));
  }

  private void batchInsertPermissionAssignments(UUID roleId, List<UUID> permissionIds) {
    jdbcTemplate.batchUpdate(
        "insert into role_permissions (role_id, permission_id) values (?, ?)",
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement statement, int index) throws SQLException {
            statement.setObject(1, roleId);
            statement.setObject(2, permissionIds.get(index));
          }

          @Override
          public int getBatchSize() {
            return permissionIds.size();
          }
        });
  }

  private RoleResponse toRoleResponse(Role role) {
    return RoleResponse.from(
        role,
        assignedUserCount(role.getId()),
        permissionIdsForRole(role.getId()),
        permissionCodesForRole(role.getId()));
  }

  private UserResponse toUserResponse(UserAccount account) {
    UUID userId = account.getId();
    List<UUID> roleIds =
        jdbcTemplate.queryForList(
            "select role_id from user_roles where user_id = ? order by role_id",
            UUID.class,
            userId);
    List<String> roleNames =
        jdbcTemplate.queryForList(
            """
            select role.name
            from user_roles assignment
            join roles role on role.id = assignment.role_id
            where assignment.user_id = ?
            order by role.name
            """,
            String.class,
            userId);
    List<String> permissionCodes =
        jdbcTemplate.queryForList(
            """
            select distinct permission.code
            from user_roles user_role
            join role_permissions role_permission on role_permission.role_id = user_role.role_id
            join permissions permission on permission.id = role_permission.permission_id
            where user_role.user_id = ?
            order by permission.code
            """,
            String.class,
            userId);
    List<UUID> branchIds =
        jdbcTemplate.queryForList(
            """
            select branch_id
            from user_branch_assignments
            where user_id = ?
            order by branch_id
            """,
            UUID.class,
            userId);
    List<String> branchNames =
        jdbcTemplate.queryForList(
            """
            select branch.name
            from user_branch_assignments assignment
            join branches branch on branch.id = assignment.branch_id
            where assignment.user_id = ?
            order by branch.name
            """,
            String.class,
            userId);

    return new UserResponse(
        account.getId(),
        account.getEmail(),
        account.getDisplayName(),
        account.getStatus(),
        roleIds,
        roleNames,
        permissionCodes,
        branchIds,
        branchNames);
  }

  private int assignedUserCount(UUID roleId) {
    Number count =
        jdbcTemplate.queryForObject(
            "select count(*) from user_roles where role_id = ?", Number.class, roleId);
    return count == null ? 0 : count.intValue();
  }

  private List<UUID> permissionIdsForRole(UUID roleId) {
    return jdbcTemplate.queryForList(
        """
        select permission.id
        from role_permissions assignment
        join permissions permission on permission.id = assignment.permission_id
        where assignment.role_id = ?
        order by permission.code
        """,
        UUID.class,
        roleId);
  }

  private List<String> permissionCodesForRole(UUID roleId) {
    return jdbcTemplate.queryForList(
        """
        select permission.code
        from role_permissions assignment
        join permissions permission on permission.id = assignment.permission_id
        where assignment.role_id = ?
        order by permission.code
        """,
        String.class,
        roleId);
  }

  private PermissionResponse requirePermission(UUID permissionId) {
    List<PermissionResponse> permissions =
        jdbcTemplate.query(
            """
            select id, code, description
            from permissions
            where id = ?
            """,
            this::permissionFromResultSet,
            permissionId);
    if (permissions.isEmpty()) {
      throw new EntityNotFoundException("Permission not found");
    }
    return permissions.get(0);
  }

  private PermissionResponse permissionFromResultSet(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new PermissionResponse(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("code"),
        resultSet.getString("description"));
  }

  private boolean permissionExists(UUID permissionId) {
    return exists("select count(*) from permissions where id = ?", permissionId);
  }

  private boolean includesProfitViewPermission(Set<UUID> permissionIds) {
    for (UUID permissionId : permissionIds) {
      if (permissionHasCode(permissionId, PROFIT_VIEW_PERMISSION)) {
        return true;
      }
    }
    return false;
  }

  private boolean includesHrPermission(Set<UUID> permissionIds) {
    for (UUID permissionId : permissionIds) {
      if (permissionCodeStartsWith(permissionId, HR_PERMISSION_PREFIX)) {
        return true;
      }
    }
    return false;
  }

  private boolean permissionHasCode(UUID permissionId, String code) {
    return exists(
        "select count(*) from permissions where id = ? and lower(code) = lower(?)",
        permissionId,
        code);
  }

  private boolean permissionCodeStartsWith(UUID permissionId, String prefix) {
    return exists(
        "select count(*) from permissions where id = ? and lower(code) like ?",
        permissionId,
        prefix.toLowerCase(Locale.ROOT) + "%");
  }

  private boolean permissionCodeExists(String code) {
    return exists("select count(*) from permissions where lower(code) = lower(?)", code);
  }

  private boolean permissionCodeExistsExcludingPermission(String code, UUID permissionId) {
    return exists(
        "select count(*) from permissions where lower(code) = lower(?) and id <> ?",
        code,
        permissionId);
  }

  private boolean exists(String sql, Object... args) {
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
    return count != null && count > 0;
  }

  private static String normalizeName(String value) {
    return value.trim();
  }

  private static String normalizeEmail(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private static String normalizeRoleKey(String value, String fallbackName) {
    String source = value == null || value.isBlank() ? fallbackName : value;
    String key =
        source
            .trim()
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("(^_+|_+$)", "");
    if (key.isBlank()) {
      throw new IllegalArgumentException("Role key is required");
    }
    return key;
  }

  private static void requireSupportedRoleKey(String key) {
    if (OWNER_ROLE_KEY.equals(key)) {
      throw new IllegalArgumentException("Owner role is no longer supported");
    }
  }

  private static String normalizePermissionCode(String value) {
    String code = value.trim().toLowerCase(Locale.ROOT);
    if (!code.matches("[a-z0-9]+([:.][a-z0-9]+)*")) {
      throw new IllegalArgumentException(
          "Permission code can use lowercase letters, numbers, colons, or dots");
    }
    return code;
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static String requiredPassword(String value) {
    if (!hasUsablePassword(value)) {
      throw new IllegalArgumentException("Password is required");
    }
    return value;
  }

  private static boolean hasUsablePassword(String value) {
    return value != null && !value.isBlank();
  }

  private static Set<UUID> normalizeIds(Set<UUID> ids) {
    LinkedHashSet<UUID> normalized = new LinkedHashSet<>();
    if (ids != null) {
      for (UUID id : ids) {
        if (id != null) {
          normalized.add(id);
        }
      }
    }
    return normalized;
  }

  private static UserStatus statusOrActive(UserStatus status) {
    return status == null ? UserStatus.ACTIVE : status;
  }
}
