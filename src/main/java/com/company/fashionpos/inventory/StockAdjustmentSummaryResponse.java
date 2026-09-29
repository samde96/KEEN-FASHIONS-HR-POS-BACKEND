package com.company.fashionpos.inventory;

import java.math.BigDecimal;

public record StockAdjustmentSummaryResponse(
    int lossItems,
    int excessItems,
    int totalLossQuantity,
    int totalExcessQuantity,
    BigDecimal totalLossValue,
    BigDecimal totalExcessValue,
    BigDecimal netValue) {

  public static StockAdjustmentSummaryResponse from(Iterable<StockAdjustment> adjustments) {
    int lossItems = 0;
    int excessItems = 0;
    int totalLossQuantity = 0;
    int totalExcessQuantity = 0;
    BigDecimal totalLossValue = BigDecimal.ZERO;
    BigDecimal totalExcessValue = BigDecimal.ZERO;

    for (StockAdjustment adjustment : adjustments) {
      if (adjustment.getVarianceQuantity() < 0) {
        lossItems++;
        totalLossQuantity += Math.abs(adjustment.getVarianceQuantity());
        totalLossValue = totalLossValue.add(adjustment.getLossValue());
      } else if (adjustment.getVarianceQuantity() > 0) {
        excessItems++;
        totalExcessQuantity += adjustment.getVarianceQuantity();
        totalExcessValue = totalExcessValue.add(adjustment.getExcessValue());
      }
    }

    return new StockAdjustmentSummaryResponse(
        lossItems,
        excessItems,
        totalLossQuantity,
        totalExcessQuantity,
        totalLossValue,
        totalExcessValue,
        totalExcessValue.subtract(totalLossValue));
  }
}
