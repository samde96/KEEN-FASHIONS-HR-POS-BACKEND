package com.company.fashionpos.sales;

import java.math.BigDecimal;
import java.util.UUID;

public record SalePaymentResponse(
    UUID id,
    PaymentMethod method,
    BigDecimal amount,
    PaymentStatus status,
    String reference,
    BigDecimal cashReceived,
    BigDecimal changeDue) {

  public static SalePaymentResponse from(SalePayment payment) {
    return new SalePaymentResponse(
        payment.getId(),
        payment.getMethod(),
        payment.getAmount(),
        payment.getStatus(),
        payment.getReference(),
        payment.getCashReceived(),
        payment.getChangeDue());
  }
}
