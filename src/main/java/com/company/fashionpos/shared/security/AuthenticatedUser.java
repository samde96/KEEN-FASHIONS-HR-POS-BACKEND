package com.company.fashionpos.shared.security;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
    UUID userId,
    String email,
    String displayName,
    UUID organizationId,
    Set<UUID> branchIds,
    Set<String> roleKeys,
    Set<String> roleNames,
    Set<String> permissionCodes) {

  private static final String ADMIN_ROLE_KEY = "ADMIN";

  public AuthenticatedUser {
    branchIds = Set.copyOf(branchIds);
    roleKeys = Set.copyOf(roleKeys);
    roleNames = Set.copyOf(roleNames);
    permissionCodes = Set.copyOf(permissionCodes);
  }

  public boolean canAccessBranch(UUID branchId) {
    return branchIds.contains(branchId);
  }

  public boolean hasPermission(String permissionCode) {
    return permissionCodes.contains(permissionCode);
  }

  public boolean isAdmin() {
    return roleKeys.contains(ADMIN_ROLE_KEY);
  }

  public boolean canViewProfit() {
    return isAdmin();
  }
}
