package com.company.fashionpos.hr;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PayrollRunResponse(
    UUID id,
    UUID payrollPeriodId,
    String payrollPeriodName,
    UUID branchId,
    String branchName,
    String name,
    PayrollRunStatus status,
    Instant processedAt,
    List<PayrollEmployeeResponse> employees) {

  public static PayrollRunResponse from(
      PayrollRun payrollRun, List<PayrollEmployeeResponse> employees) {
    return new PayrollRunResponse(
        payrollRun.getId(),
        payrollRun.getPayrollPeriod().getId(),
        payrollRun.getPayrollPeriod().getName(),
        payrollRun.getBranch() == null ? null : payrollRun.getBranch().getId(),
        payrollRun.getBranch() == null ? null : payrollRun.getBranch().getName(),
        payrollRun.getName(),
        payrollRun.getStatus(),
        payrollRun.getProcessedAt(),
        employees);
  }
}
