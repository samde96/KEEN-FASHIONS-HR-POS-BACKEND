package com.company.fashionpos.mpesa;

import com.company.fashionpos.sales.SaleRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class MpesaDtos {
  private MpesaDtos() {}

  public record StartRequest(
      @NotBlank @Size(max = 20) String phoneNumber, @NotNull @Valid SaleRequest sale) {}

  public record Payment(
      UUID id,
      String status,
      BigDecimal amount,
      String phoneMask,
      String receiptNumber,
      String message,
      UUID saleId,
      Instant createdAt) {}

  record Transaction(
      UUID id,
      UUID organizationId,
      UUID branchId,
      UUID userId,
      String idempotencyKey,
      String requestHash,
      String saleHash,
      String phoneCiphertext,
      String phoneHash,
      String phoneMask,
      BigDecimal amount,
      String status,
      String merchantRequestId,
      String checkoutRequestId,
      String receiptNumber,
      Integer resultCode,
      String message,
      UUID saleId,
      Instant createdAt,
      int queryAttempts) {
    Payment payment() {
      return new Payment(id, status, amount, phoneMask, receiptNumber, message, saleId, createdAt);
    }
  }
}
