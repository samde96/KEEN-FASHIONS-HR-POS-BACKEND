package com.company.fashionpos.catalog;

import java.math.BigDecimal;

public enum VatCategory {
  A(new BigDecimal("0.16")),
  G(BigDecimal.ZERO);

  private final BigDecimal rate;

  VatCategory(BigDecimal rate) {
    this.rate = rate;
  }

  public BigDecimal rate() {
    return rate;
  }
}
