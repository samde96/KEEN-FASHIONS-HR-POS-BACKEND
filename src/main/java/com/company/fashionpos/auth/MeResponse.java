package com.company.fashionpos.auth;

import com.company.fashionpos.shared.security.AuthenticatedUser;
import java.util.Set;
import java.util.UUID;

public record MeResponse(
    UUID userId,
    String email,
    String displayName,
    UUID organizationId,
    Set<UUID> branchIds,
    Set<String> roleKeys,
    Set<String> roleNames,
    Set<String> permissionCodes) {

  public static MeResponse from(AuthenticatedUser user) {
    return new MeResponse(
        user.userId(),
        user.email(),
        user.displayName(),
        user.organizationId(),
        user.branchIds(),
        user.roleKeys(),
        user.roleNames(),
        user.permissionCodes());
  }
}
