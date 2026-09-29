package com.company.fashionpos.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockAdjustmentResponse(
    UUID id,
    UUID branchId,
    String branchName,
    UUID productId,
    String productName,
    String sku,
    int systemQuantity,
    int countedQuantity,
    int varianceQuantity,
    BigDecimal unitCost,
    BigDecimal lossValue,
    BigDecimal excessValue,
    String reason,
    String notes,
    Instant adjustedAt) {

  public static StockAdjustmentResponse from(StockAdjustment adjustment) {
    return new StockAdjustmentResponse(
        adjustment.getId(),
        adjustment.getBranch().getId(),
        adjustment.getBranch().getName(),
        adjustment.getProduct().getId(),
        adjustment.getProduct().getName(),
        adjustment.getProduct().getSku(),
        adjustment.getSystemQuantity(),
        adjustment.getCountedQuantity(),
        adjustment.getVarianceQuantity(),
        adjustment.getUnitCost(),
        adjustment.getLossValue(),
        adjustment.getExcessValue(),
        adjustment.getReason(),
        adjustment.getNotes(),
        adjustment.getAdjustedAt());
  }
}
