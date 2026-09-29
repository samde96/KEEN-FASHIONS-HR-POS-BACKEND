package com.company.fashionpos.hr;

import java.math.BigDecimal;
import java.util.UUID;

public record PayrollEmployeeResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    String employeeNumber,
    String branchName,
    BigDecimal grossPay,
    BigDecimal basicSalary,
    BigDecimal bonusAmount,
    BigDecimal automaticBonusAmount,
    BigDecimal manualBonusAmount,
    BigDecimal lossAmount,
    BigDecimal salesAmount,
    int qualifyingSalesDays,
    BigDecimal salesBonusTarget,
    BigDecimal salesBonusPerDay,
    Integer performanceScore,
    BigDecimal totalDeductions,
    BigDecimal netPay,
    String paymentMethod,
    String paymentDestination,
    String notes) {

  public static PayrollEmployeeResponse from(PayrollEmployee payrollEmployee) {
    return new PayrollEmployeeResponse(
        payrollEmployee.getId(),
        payrollEmployee.getEmployee().getId(),
        EmployeeProfileResponse.fullName(payrollEmployee.getEmployee()),
        payrollEmployee.getEmployee().getEmployeeNumber(),
        payrollEmployee.getEmployee().getPrimaryBranch().getName(),
        payrollEmployee.getGrossPay(),
        payrollEmployee.getBasicSalary(),
        payrollEmployee.getBonusAmount(),
        payrollEmployee.getAutomaticBonusAmount(),
        payrollEmployee.getManualBonusAmount(),
        payrollEmployee.getLossAmount(),
        payrollEmployee.getSalesAmount(),
        payrollEmployee.getQualifyingSalesDays(),
        payrollEmployee.getSalesBonusTarget(),
        payrollEmployee.getSalesBonusPerDay(),
        payrollEmployee.getPerformanceScore(),
        payrollEmployee.getTotalDeductions(),
        payrollEmployee.getNetPay(),
        payrollEmployee.getPaymentMethod(),
        payrollEmployee.getPaymentDestination(),
        payrollEmployee.getNotes());
  }
}
