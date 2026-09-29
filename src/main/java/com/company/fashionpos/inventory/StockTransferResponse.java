package com.company.fashionpos.inventory;

import java.time.Instant;
import java.util.UUID;

public record StockTransferResponse(
    UUID id,
    UUID sourceBranchId,
    String sourceBranchName,
    UUID destinationBranchId,
    String destinationBranchName,
    UUID productId,
    String productName,
    String sku,
    int quantity,
    StockTransferStatus status,
    String referenceNumber,
    String notes,
    Instant transferredAt) {

  public static StockTransferResponse from(StockTransfer transfer) {
    return new StockTransferResponse(
        transfer.getId(),
        transfer.getSourceBranch().getId(),
        transfer.getSourceBranch().getName(),
        transfer.getDestinationBranch().getId(),
        transfer.getDestinationBranch().getName(),
        transfer.getProduct().getId(),
        transfer.getProduct().getName(),
        transfer.getProduct().getSku(),
        transfer.getQuantity(),
        transfer.getStatus(),
        transfer.getReferenceNumber(),
        transfer.getNotes(),
        transfer.getTransferredAt());
  }
}
