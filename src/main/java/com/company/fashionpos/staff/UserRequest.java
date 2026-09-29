package com.company.fashionpos.staff;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record UserRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 160) String displayName,
    UserStatus status,
    Set<UUID> roleIds,
    Set<UUID> branchIds) {}
