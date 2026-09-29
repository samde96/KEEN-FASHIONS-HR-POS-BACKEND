package com.company.fashionpos.hr;

import java.util.List;

public record PayrollPageResponse(
    List<PayrollComponentResponse> components,
    List<PayrollPeriodResponse> periods,
    List<PayrollRunResponse> runs,
    PayrollSalesBonusRuleResponse salesBonusRule) {}
