package com.company.fashionpos.operations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DailySalesReport(
    UUID id,
    UUID branchId,
    String branchName,
    LocalDate businessDate,
    String timeZone,
    Instant fromInclusive,
    Instant toExclusive,
    String currencyCode,
    long transactions,
    BigDecimal grossSales,
    BigDecimal discounts,
    BigDecimal tax,
    BigDecimal refunds,
    long refundTransactions,
    BigDecimal cancellations,
    long cancelledTransactions,
    BigDecimal netSales,
    BigDecimal totalRevenue,
    Map<String, BigDecimal> payments,
    long itemsSold,
    List<Map<String, Object>> topVariants,
    List<Map<String, Object>> parentProducts,
    List<Map<String, Object>> cashiers) {}
