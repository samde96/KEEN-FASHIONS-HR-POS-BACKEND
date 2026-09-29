package com.company.fashionpos.hr;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PayrollEmployeeAdjustmentRequest(
    @DecimalMin("0.00") BigDecimal bonusAmount,
    @DecimalMin("0.00") BigDecimal lossAmount,
    @Size(max = 1000) String notes) {}
