package com.company.fashionpos.hr;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PayrollComponentRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 40) String code,
    @NotNull PayrollComponentType componentType,
    Boolean taxable,
    @NotNull @DecimalMin("0.00") BigDecimal defaultAmount,
    Boolean active) {}
