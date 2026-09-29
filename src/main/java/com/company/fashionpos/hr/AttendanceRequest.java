package com.company.fashionpos.hr;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendanceRequest(
    @NotNull UUID employeeId,
    @NotNull UUID branchId,
    @NotNull LocalDate attendanceDate,
    OffsetDateTime clockIn,
    OffsetDateTime clockOut,
    @NotNull AttendanceStatus status,
    @Size(max = 1000) String notes,
    @Size(max = 500) String correctionReason) {}
