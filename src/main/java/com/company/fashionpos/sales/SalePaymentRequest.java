package com.company.fashionpos.sales;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SalePaymentRequest(
    @NotNull PaymentMethod method,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @Size(max = 80) String paymentReference,
    @DecimalMin("0.00") BigDecimal cashReceived) {}
