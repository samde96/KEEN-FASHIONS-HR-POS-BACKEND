package com.company.fashionpos.staff;

import java.util.List;
import java.util.UUID;

public record RoleResponse(
    UUID id,
    String name,
    String key,
    String description,
    int assignedUsers,
    List<UUID> permissionIds,
    List<String> permissionCodes) {

  public static RoleResponse from(
      Role role, int assignedUsers, List<UUID> permissionIds, List<String> permissionCodes) {
    return new RoleResponse(
        role.getId(),
        role.getName(),
        role.getKey(),
        role.getDescription(),
        assignedUsers,
        permissionIds,
        permissionCodes);
  }
}
