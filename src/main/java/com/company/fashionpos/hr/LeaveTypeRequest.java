package com.company.fashionpos.hr;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeaveTypeRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 40) String code,
    @Size(max = 500) String description,
    Boolean requiresBalance,
    @Min(0) @Max(366) Integer defaultDays,
    Boolean active) {}
