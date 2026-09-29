package com.company.fashionpos.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockIntakeResponse(
    UUID id,
    UUID branchId,
    String branchName,
    UUID productId,
    String productName,
    String sku,
    String supplierName,
    String referenceNumber,
    int quantity,
    BigDecimal unitCost,
    String notes,
    Instant receivedAt) {

  public static StockIntakeResponse from(StockIntake intake) {
    return new StockIntakeResponse(
        intake.getId(),
        intake.getBranch().getId(),
        intake.getBranch().getName(),
        intake.getProduct().getId(),
        intake.getProduct().getName(),
        intake.getProduct().getSku(),
        intake.getSupplierName(),
        intake.getReferenceNumber(),
        intake.getQuantity(),
        intake.getUnitCost(),
        intake.getNotes(),
        intake.getReceivedAt());
  }
}
