package com.company.fashionpos.inventory;

import com.company.fashionpos.catalog.VatCategory;
import java.math.BigDecimal;
import java.util.UUID;

public record InventoryResponse(
    UUID id,
    UUID branchId,
    String branchName,
    UUID productId,
    String productName,
    String sku,
    String categoryName,
    VatCategory vatCategory,
    BigDecimal unitPrice,
    int quantityOnHand,
    int quantityReserved,
    int quantityAvailable,
    int reorderLevel,
    String stockHealth) {

  public static InventoryResponse from(InventoryItem item) {
    int available = item.availableQuantity();
    String stockHealth =
        available == 0
            ? "OUT_OF_STOCK"
            : available <= item.getReorderLevel() && item.getReorderLevel() > 0
                ? "LOW_STOCK"
                : "HEALTHY";

    return new InventoryResponse(
        item.getId(),
        item.getBranch().getId(),
        item.getBranch().getName(),
        item.getProduct().getId(),
        item.getProduct().getName(),
        item.getProduct().getSku(),
        item.getProduct().getCategory().getName(),
        item.getProduct().getVatCategory(),
        item.getProduct().getUnitPrice(),
        item.getQuantityOnHand(),
        item.getQuantityReserved(),
        available,
        item.getReorderLevel(),
        stockHealth);
  }
}
