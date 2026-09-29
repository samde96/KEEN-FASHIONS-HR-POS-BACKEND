package com.company.fashionpos.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StockAdjustmentRequest(
    @NotNull UUID branchId,
    @NotNull UUID productId,
    @Min(0) int countedQuantity,
    @NotBlank @Size(max = 160) String reason,
    @Size(max = 500) String notes) {}
