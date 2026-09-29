package com.company.fashionpos.expenses;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseRequest(
    @NotNull UUID branchId,
    @NotBlank @Size(max = 80) String category,
    @NotBlank @Size(max = 180) String description,
    @Size(max = 160) String vendorName,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotNull ExpensePaymentMethod paymentMethod,
    @Size(max = 80) String paymentReference,
    @NotNull ExpenseStatus status,
    @Size(max = 500) String notes) {}
