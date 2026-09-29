package com.company.fashionpos.hr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    String employeeNumber,
    UUID branchId,
    String branchName,
    UUID leaveTypeId,
    String leaveTypeName,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal requestedDays,
    String reason,
    UUID approverUserId,
    String approverUserDisplayName,
    String approverComments,
    LeaveRequestStatus status,
    Instant submittedAt,
    Instant decidedAt) {

  public static LeaveRequestResponse from(LeaveRequest request) {
    return new LeaveRequestResponse(
        request.getId(),
        request.getEmployee().getId(),
        EmployeeProfileResponse.fullName(request.getEmployee()),
        request.getEmployee().getEmployeeNumber(),
        request.getBranch().getId(),
        request.getBranch().getName(),
        request.getLeaveType().getId(),
        request.getLeaveType().getName(),
        request.getStartDate(),
        request.getEndDate(),
        request.getRequestedDays(),
        request.getReason(),
        request.getApproverUser() == null ? null : request.getApproverUser().getId(),
        request.getApproverUser() == null ? null : request.getApproverUser().getDisplayName(),
        request.getApproverComments(),
        request.getStatus(),
        request.getSubmittedAt(),
        request.getDecidedAt());
  }
}
