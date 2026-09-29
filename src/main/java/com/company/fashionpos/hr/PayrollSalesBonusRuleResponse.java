package com.company.fashionpos.hr;

import java.math.BigDecimal;

public record PayrollSalesBonusRuleResponse(
    BigDecimal dailySalesTarget, BigDecimal bonusPerTargetDay, boolean active) {

  public static PayrollSalesBonusRuleResponse from(PayrollSalesBonusRule rule) {
    return new PayrollSalesBonusRuleResponse(
        rule.getDailySalesTarget(), rule.getBonusPerTargetDay(), rule.isActive());
  }

  public static PayrollSalesBonusRuleResponse empty() {
    return new PayrollSalesBonusRuleResponse(
        BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), true);
  }
}
