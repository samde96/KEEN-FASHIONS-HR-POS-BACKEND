package com.company.fashionpos.hr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeePerformanceEntryResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    String employeeNumber,
    UUID branchId,
    String branchName,
    EmployeePerformanceEntryType entryType,
    LocalDate entryDate,
    String title,
    BigDecimal amount,
    Integer score,
    String notes,
    UUID createdByUserId,
    String createdByUserDisplayName) {

  public static EmployeePerformanceEntryResponse from(EmployeePerformanceEntry entry) {
    return new EmployeePerformanceEntryResponse(
        entry.getId(),
        entry.getEmployee().getId(),
        EmployeeProfileResponse.fullName(entry.getEmployee()),
        entry.getEmployee().getEmployeeNumber(),
        entry.getBranch().getId(),
        entry.getBranch().getName(),
        entry.getEntryType(),
        entry.getEntryDate(),
        entry.getTitle(),
        entry.getAmount(),
        entry.getScore(),
        entry.getNotes(),
        entry.getCreatedByUser() == null ? null : entry.getCreatedByUser().getId(),
        entry.getCreatedByUser() == null ? null : entry.getCreatedByUser().getDisplayName());
  }
}
