package com.company.fashionpos.customers;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerSaleActivityResponse(
    String reference,
    String branchName,
    Instant occurredAt,
    BigDecimal boughtAmount,
    BigDecimal paidAmount) {}
