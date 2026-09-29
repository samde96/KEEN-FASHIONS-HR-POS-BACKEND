package com.company.fashionpos.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductCategoryRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank
        @Size(max = 40)
        @Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$",
            message =
                "must start with a letter or number and contain only letters, numbers, underscores, or hyphens")
        String code,
    CatalogStatus status) {}
