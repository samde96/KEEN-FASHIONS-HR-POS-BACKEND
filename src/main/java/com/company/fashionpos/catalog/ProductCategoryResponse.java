package com.company.fashionpos.catalog;

import java.util.UUID;

public record ProductCategoryResponse(UUID id, String name, String code, CatalogStatus status) {

  public static ProductCategoryResponse from(ProductCategory category) {
    return new ProductCategoryResponse(
        category.getId(), category.getName(), category.getCode(), category.getStatus());
  }
}
