package com.company.fashionpos.hr;

import java.util.UUID;

public record LeaveTypeResponse(
    UUID id,
    String name,
    String code,
    String description,
    boolean requiresBalance,
    int defaultDays,
    boolean active) {

  public static LeaveTypeResponse from(LeaveType leaveType) {
    return new LeaveTypeResponse(
        leaveType.getId(),
        leaveType.getName(),
        leaveType.getCode(),
        leaveType.getDescription(),
        leaveType.isRequiresBalance(),
        leaveType.getDefaultDays(),
        leaveType.isActive());
  }
}
