package com.company.fashionpos.shared.security;

import com.company.fashionpos.staff.UserAccount;
import com.company.fashionpos.staff.UserAccountRepository;
import com.company.fashionpos.staff.UserBranchAssignmentRepository;
import com.company.fashionpos.staff.UserStatus;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedUserService {

  private final UserAccountRepository userAccountRepository;
  private final UserBranchAssignmentRepository userBranchAssignmentRepository;
  private final JdbcTemplate jdbcTemplate;

  public AuthenticatedUserService(
      UserAccountRepository userAccountRepository,
      UserBranchAssignmentRepository userBranchAssignmentRepository,
      JdbcTemplate jdbcTemplate) {
    this.userAccountRepository = userAccountRepository;
    this.userBranchAssignmentRepository = userBranchAssignmentRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public AuthenticatedUser current(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new AccessDeniedException("Authenticated user is required");
    }

    UserAccount user =
        userAccountRepository
            .findByEmailIgnoreCaseAndStatus(principal.getName(), UserStatus.ACTIVE)
            .orElseThrow(() -> new AccessDeniedException("Authenticated user is not active"));

    Set<java.util.UUID> branchIds =
        userBranchAssignmentRepository.findAuthorizedBranchIds(
            user.getId(), user.getOrganization().getId());

    return new AuthenticatedUser(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getOrganization().getId(),
        branchIds,
        rolesForUser(user.getId(), "role_key"),
        rolesForUser(user.getId(), "name"),
        permissionCodesForUser(user.getId()));
  }

  private Set<String> rolesForUser(java.util.UUID userId, String columnName) {
    List<String> values =
        jdbcTemplate.queryForList(
            """
            select role.%s
            from user_roles assignment
            join roles role on role.id = assignment.role_id
            where assignment.user_id = ?
            order by role.name
            """
                .formatted(columnName),
            String.class,
            userId);
    return new LinkedHashSet<>(values);
  }

  private Set<String> permissionCodesForUser(java.util.UUID userId) {
    List<String> values =
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
    return new LinkedHashSet<>(values);
  }
}
