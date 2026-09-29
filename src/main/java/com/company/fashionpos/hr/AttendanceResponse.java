package com.company.fashionpos.hr;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendanceResponse(
    UUID id,
    UUID employeeId,
    String employeeNumber,
    String employeeName,
    UUID branchId,
    String branchName,
    String departmentName,
    String jobTitleTitle,
    LocalDate attendanceDate,
    OffsetDateTime clockIn,
    OffsetDateTime clockOut,
    AttendanceStatus status,
    int workedMinutes,
    int overtimeMinutes,
    int lateMinutes,
    String notes,
    String correctionReason,
    boolean corrected) {

  public static AttendanceResponse from(AttendanceRecord attendance) {
    return new AttendanceResponse(
        attendance.getId(),
        attendance.getEmployee().getId(),
        attendance.getEmployee().getEmployeeNumber(),
        EmployeeProfileResponse.fullName(attendance.getEmployee()),
        attendance.getBranch().getId(),
        attendance.getBranch().getName(),
        attendance.getEmployee().getDepartment() == null
            ? null
            : attendance.getEmployee().getDepartment().getName(),
        attendance.getEmployee().getJobTitle() == null
            ? null
            : attendance.getEmployee().getJobTitle().getTitle(),
        attendance.getAttendanceDate(),
        attendance.getClockIn(),
        attendance.getClockOut(),
        attendance.getStatus(),
        attendance.getWorkedMinutes(),
        attendance.getOvertimeMinutes(),
        attendance.getLateMinutes(),
        attendance.getNotes(),
        attendance.getCorrectionReason(),
        attendance.isCorrected());
  }
}
