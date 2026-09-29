package com.company.fashionpos.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductRequest(
    @NotBlank @Size(max = 180) String name,
    @Size(max = 80) String sku,
    @Size(max = 80) String barcode,
    @NotNull UUID categoryId,
    @Size(max = 80) String department,
    @Size(max = 500) String description,
    @Size(max = 1500000) String imageUrl,
    @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
    @NotNull @DecimalMin("0.00") BigDecimal costPrice,
    VatCategory vatCategory,
    List<@Size(max = 40) String> sizes,
    List<@Size(max = 40) String> colors,
    CatalogStatus status) {}
