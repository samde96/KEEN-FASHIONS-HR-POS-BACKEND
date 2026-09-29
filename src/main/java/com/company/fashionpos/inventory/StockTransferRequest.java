package com.company.fashionpos.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StockTransferRequest(
    @NotNull UUID sourceBranchId,
    @NotNull UUID destinationBranchId,
    @NotNull UUID productId,
    @Positive int quantity,
    @Size(max = 80) String referenceNumber,
    @Size(max = 500) String notes) {}
