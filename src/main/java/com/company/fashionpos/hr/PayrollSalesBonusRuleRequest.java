package com.company.fashionpos.hr;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PayrollSalesBonusRuleRequest(
    @NotNull @DecimalMin("0.00") BigDecimal dailySalesTarget,
    @NotNull @DecimalMin("0.00") BigDecimal bonusPerTargetDay,
    Boolean active) {}
