package com.company.fashionpos.shifts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashierShiftResponse(
    UUID id,
    UUID branchId,
    String branchName,
    UUID cashierId,
    String cashierName,
    String status,
    BigDecimal openingFloat,
    String openingNote,
    Instant openedAt,
    Instant closedAt,
    BigDecimal closingCash,
    String closingNote,
    BigDecimal cashSales,
    BigDecimal cashExpenses,
    BigDecimal totalSales,
    int transactionCount,
    BigDecimal expectedCash,
    BigDecimal variance) {}
