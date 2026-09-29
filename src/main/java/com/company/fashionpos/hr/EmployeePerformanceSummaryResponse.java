package com.company.fashionpos.hr;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EmployeePerformanceSummaryResponse(
    UUID employeeId,
    String employeeName,
    String employeeNumber,
    UUID branchId,
    String branchName,
    UUID departmentId,
    String departmentName,
    String jobTitleTitle,
    BigDecimal basicSalary,
    SalaryPaymentMethod salaryPaymentMethod,
    String paymentDestination,
    BigDecimal actualSalesAmount,
    BigDecimal manualSalesAmount,
    BigDecimal totalSalesAmount,
    BigDecimal bonusAmount,
    BigDecimal automaticBonusAmount,
    BigDecimal manualBonusAmount,
    BigDecimal lossAmount,
    BigDecimal projectedNetPay,
    int qualifyingSalesDays,
    BigDecimal salesBonusTarget,
    BigDecimal salesBonusPerDay,
    Integer averagePerformanceScore,
    int entryCount,
    List<EmployeeSalesDayResponse> salesDays) {}
