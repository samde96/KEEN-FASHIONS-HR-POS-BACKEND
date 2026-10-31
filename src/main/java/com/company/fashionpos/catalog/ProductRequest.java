package com.company.fashionpos.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
    CatalogStatus status,
    ProductType productType,
    UUID parentProductId,
    @Size(max = 12)
        Map<@NotBlank @Size(max = 40) String, @NotBlank @Size(max = 80) String> variantAttributes,
    @Min(0) Integer reorderLevel) {
  public ProductRequest(
      String name,
      String sku,
      String barcode,
      UUID categoryId,
      String department,
      String description,
      String imageUrl,
      BigDecimal unitPrice,
      BigDecimal costPrice,
      VatCategory vatCategory,
      List<String> sizes,
      List<String> colors,
      CatalogStatus status) {
    this(
        name,
        sku,
        barcode,
        categoryId,
        department,
        description,
        imageUrl,
        unitPrice,
        costPrice,
        vatCategory,
        sizes,
        colors,
        status,
        null,
        null,
        null,
        null);
  }
}
