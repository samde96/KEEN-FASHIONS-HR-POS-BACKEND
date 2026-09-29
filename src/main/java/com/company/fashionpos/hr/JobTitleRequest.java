package com.company.fashionpos.hr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record JobTitleRequest(
    @NotBlank @Size(max = 120) String title,
    @NotBlank
        @Size(max = 40)
        @Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$",
            message =
                "must start with a letter or number and contain only letters, numbers, underscores, or hyphens")
        String code,
    @NotNull UUID departmentId,
    @Size(max = 500) String description,
    Boolean active) {}
