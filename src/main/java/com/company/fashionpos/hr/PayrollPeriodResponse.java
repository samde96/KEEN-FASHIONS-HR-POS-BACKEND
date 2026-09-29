package com.company.fashionpos.hr;

import java.time.LocalDate;
import java.util.UUID;

public record PayrollPeriodResponse(
    UUID id,
    UUID branchId,
    String branchName,
    String name,
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate paymentDate,
    PayrollPeriodStatus status) {

  public static PayrollPeriodResponse from(PayrollPeriod period) {
    return new PayrollPeriodResponse(
        period.getId(),
        period.getBranch() == null ? null : period.getBranch().getId(),
        period.getBranch() == null ? null : period.getBranch().getName(),
        period.getName(),
        period.getPeriodStart(),
        period.getPeriodEnd(),
        period.getPaymentDate(),
        period.getStatus());
  }
}
