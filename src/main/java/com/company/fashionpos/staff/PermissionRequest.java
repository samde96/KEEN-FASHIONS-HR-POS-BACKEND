package com.company.fashionpos.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermissionRequest(
    @NotBlank @Size(max = 120) String code, @Size(max = 255) String description) {}
