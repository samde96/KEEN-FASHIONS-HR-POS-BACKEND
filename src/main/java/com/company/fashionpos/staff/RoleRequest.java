package com.company.fashionpos.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record RoleRequest(
    @NotBlank @Size(max = 80) String name,
    @Size(max = 80) String key,
    @Size(max = 255) String description,
    Set<UUID> permissionIds) {}
