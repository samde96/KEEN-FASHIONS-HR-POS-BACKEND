package com.company.fashionpos.hr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollPeriodRequest(
    UUID branchId,
    @NotBlank @Size(max = 120) String name,
    @NotNull LocalDate periodStart,
    @NotNull LocalDate periodEnd,
    LocalDate paymentDate,
    @NotNull PayrollPeriodStatus status) {}
