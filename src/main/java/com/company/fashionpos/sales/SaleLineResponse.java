package com.company.fashionpos.sales;

import com.company.fashionpos.catalog.VatCategory;
import java.math.BigDecimal;
import java.util.UUID;

public record SaleLineResponse(
    UUID productId,
    String productName,
    String sku,
    String categoryName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal costPrice,
    BigDecimal lineTotal,
    VatCategory vatCategory,
    BigDecimal taxRate,
    BigDecimal taxAmount) {

  public static SaleLineResponse from(SaleLine line) {
    return from(line, true);
  }

  public static SaleLineResponse from(SaleLine line, boolean includeCostPrice) {
    return new SaleLineResponse(
        line.getProduct().getId(),
        line.getProductName(),
        line.getSku(),
        line.getCategoryName(),
        line.getQuantity(),
        line.getUnitPrice(),
        includeCostPrice ? line.getCostPrice() : null,
        line.getLineTotal(),
        line.getVatCategory(),
        line.getTaxRate(),
        line.getTaxAmount());
  }
}
