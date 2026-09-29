package com.company.fashionpos.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record StockIntakeRequest(
    @NotNull UUID branchId,
    @NotNull UUID productId,
    @NotBlank @Size(max = 160) String supplierName,
    @Size(max = 80) String referenceNumber,
    @Positive int quantity,
    @DecimalMin("0.00") BigDecimal unitCost,
    @Size(max = 500) String notes) {}
