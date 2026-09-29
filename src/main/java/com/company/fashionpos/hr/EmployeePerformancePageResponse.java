package com.company.fashionpos.hr;

import java.time.LocalDate;
import java.util.List;

public record EmployeePerformancePageResponse(
    LocalDate fromDate,
    LocalDate toDate,
    PayrollSalesBonusRuleResponse salesBonusRule,
    List<EmployeePerformanceSummaryResponse> summaries,
    List<EmployeePerformanceEntryResponse> entries) {}
