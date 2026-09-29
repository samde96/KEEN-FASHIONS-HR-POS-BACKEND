package com.company.fashionpos.hr;

import java.math.BigDecimal;
import java.util.UUID;

public record PayrollComponentResponse(
    UUID id,
    String name,
    String code,
    PayrollComponentType componentType,
    boolean taxable,
    BigDecimal defaultAmount,
    boolean active) {

  public static PayrollComponentResponse from(PayrollComponent component) {
    return new PayrollComponentResponse(
        component.getId(),
        component.getName(),
        component.getCode(),
        component.getComponentType(),
        component.isTaxable(),
        component.getDefaultAmount(),
        component.isActive());
  }
}
