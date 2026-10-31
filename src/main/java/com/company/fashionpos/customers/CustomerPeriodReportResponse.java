package com.company.fashionpos.customers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CustomerPeriodReportResponse(
    UUID customerId,
    String customerName,
    LocalDate from,
    LocalDate to,
    BigDecimal boughtAmount,
    BigDecimal paidAmount,
    BigDecimal balanceAmount,
    int saleCount,
    List<CustomerSaleActivityResponse> activities) {}
