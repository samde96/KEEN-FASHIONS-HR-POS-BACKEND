package com.company.fashionpos.hr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PayrollRunRequest(
    @NotNull UUID payrollPeriodId,
    @NotBlank @Size(max = 120) String name,
    @NotNull PayrollRunStatus status) {}
