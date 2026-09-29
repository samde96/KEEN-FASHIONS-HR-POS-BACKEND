package com.company.fashionpos.hr;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeePerformanceEntryRequest(
    @NotNull UUID employeeId,
    @NotNull EmployeePerformanceEntryType entryType,
    @NotNull LocalDate entryDate,
    @Size(max = 160) String title,
    @DecimalMin("0.00") BigDecimal amount,
    @Min(0) @Max(100) Integer score,
    @Size(max = 1000) String notes) {}
