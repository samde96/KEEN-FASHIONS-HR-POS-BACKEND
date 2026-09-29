package com.company.fashionpos.hr;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestInput(
    @NotNull UUID employeeId,
    @NotNull UUID leaveTypeId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @Size(max = 2000) String reason,
    UUID approverUserId,
    @Size(max = 1000) String approverComments,
    @NotNull LeaveRequestStatus status) {}
