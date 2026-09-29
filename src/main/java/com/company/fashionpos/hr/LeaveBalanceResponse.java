package com.company.fashionpos.hr;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaveBalanceResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    UUID leaveTypeId,
    String leaveTypeName,
    BigDecimal balanceDays,
    BigDecimal usedDays,
    BigDecimal availableDays) {

  public static LeaveBalanceResponse from(LeaveBalance balance) {
    return new LeaveBalanceResponse(
        balance.getId(),
        balance.getEmployee().getId(),
        EmployeeProfileResponse.fullName(balance.getEmployee()),
        balance.getLeaveType().getId(),
        balance.getLeaveType().getName(),
        balance.getBalanceDays(),
        balance.getUsedDays(),
        balance.getBalanceDays().subtract(balance.getUsedDays()));
  }
}
