package com.company.fashionpos.staff;

import java.util.List;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String displayName,
    UserStatus status,
    List<UUID> roleIds,
    List<String> roleNames,
    List<String> permissionCodes,
    List<UUID> branchIds,
    List<String> branchNames) {}
