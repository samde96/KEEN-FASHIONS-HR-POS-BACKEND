package com.company.fashionpos.branch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BranchRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank
        @Size(max = 24)
        @Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$",
            message =
                "must start with a letter or number and contain only letters, numbers, underscores, or hyphens")
        String code,
    @NotBlank @Size(max = 64) String timeZone,
    BranchStatus status) {}
