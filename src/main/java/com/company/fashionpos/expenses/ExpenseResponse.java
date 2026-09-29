package com.company.fashionpos.expenses;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExpenseResponse(
    UUID id,
    String expenseNumber,
    UUID branchId,
    String branchName,
    String category,
    String description,
    String vendorName,
    BigDecimal amount,
    ExpensePaymentMethod paymentMethod,
    String paymentReference,
    ExpenseStatus status,
    String notes,
    Instant incurredAt) {

  public static ExpenseResponse from(Expense expense) {
    return new ExpenseResponse(
        expense.getId(),
        expense.getExpenseNumber(),
        expense.getBranch().getId(),
        expense.getBranch().getName(),
        expense.getCategory(),
        expense.getDescription(),
        expense.getVendorName(),
        expense.getAmount(),
        expense.getPaymentMethod(),
        expense.getPaymentReference(),
        expense.getStatus(),
        expense.getNotes(),
        expense.getIncurredAt());
  }
}
