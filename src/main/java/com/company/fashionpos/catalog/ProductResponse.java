package com.company.fashionpos.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String name,
    String sku,
    String barcode,
    UUID categoryId,
    String categoryName,
    String department,
    String description,
    String imageUrl,
    BigDecimal unitPrice,
    BigDecimal costPrice,
    VatCategory vatCategory,
    List<String> sizes,
    List<String> colors,
    CatalogStatus status,
    int totalStock,
    ProductType productType,
    UUID parentProductId,
    String parentProductName,
    String variantName,
    Map<String, String> variantAttributes,
    int reorderLevel,
    boolean sellable) {

  public static ProductResponse from(
      ProductItem product, List<String> sizes, List<String> colors, int totalStock) {
    return from(product, sizes, colors, totalStock, true);
  }

  public static ProductResponse from(
      ProductItem product,
      List<String> sizes,
      List<String> colors,
      int totalStock,
      boolean includeCostPrice) {
    return new ProductResponse(
        product.getId(),
        product.getDisplayName(),
        product.getSku(),
        product.getBarcode(),
        product.getCategory().getId(),
        product.getCategory().getName(),
        product.getDepartment(),
        product.getDescription(),
        product.getImageUrl(),
        product.getUnitPrice(),
        includeCostPrice ? product.getCostPrice() : null,
        product.getVatCategory(),
        sizes,
        colors,
        product.getStatus(),
        totalStock,
        product.getProductType(),
        product.getParentProduct() == null ? null : product.getParentProduct().getId(),
        product.getParentProduct() == null ? null : product.getParentProduct().getName(),
        product.getParentProduct() == null ? null : product.getName(),
        product.getVariantAttributes(),
        product.getDefaultReorderLevel(),
        product.isSellable());
  }
}
