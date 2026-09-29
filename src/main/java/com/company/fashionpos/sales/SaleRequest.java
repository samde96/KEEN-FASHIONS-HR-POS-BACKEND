package com.company.fashionpos.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SaleRequest(
    @NotNull UUID branchId,
    UUID soldByEmployeeId,
    @Size(max = 160) String customerName,
    PaymentMethod paymentMethod,
    @Size(max = 80) String paymentReference,
    @DecimalMin("0.00") BigDecimal cashReceived,
    @DecimalMin("0.00") BigDecimal discountAmount,
    List<@Valid SalePaymentRequest> payments,
    @NotEmpty List<@Valid SaleLineRequest> lines) {}
