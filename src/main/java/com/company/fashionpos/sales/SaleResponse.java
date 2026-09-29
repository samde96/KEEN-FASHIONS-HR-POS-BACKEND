package com.company.fashionpos.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
    UUID id,
    String saleNumber,
    UUID branchId,
    String branchName,
    UUID soldByEmployeeId,
    String soldByEmployeeName,
    String soldByEmployeeNumber,
    String customerName,
    BigDecimal subtotalAmount,
    BigDecimal discountAmount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    SaleStatus status,
    Instant soldAt,
    List<SaleLineResponse> lines,
    List<SalePaymentResponse> payments) {

  public static SaleResponse from(Sale sale, List<SaleLine> lines, List<SalePayment> payments) {
    return from(sale, lines, payments, true);
  }

  public static SaleResponse from(
      Sale sale, List<SaleLine> lines, List<SalePayment> payments, boolean includeCostPrice) {
    return new SaleResponse(
        sale.getId(),
        sale.getSaleNumber(),
        sale.getBranch().getId(),
        sale.getBranch().getName(),
        sale.getSoldByEmployee() == null ? null : sale.getSoldByEmployee().getId(),
        sale.getSoldByEmployee() == null
            ? null
            : com.company.fashionpos.hr.EmployeeProfileResponse.fullName(sale.getSoldByEmployee()),
        sale.getSoldByEmployee() == null ? null : sale.getSoldByEmployee().getEmployeeNumber(),
        sale.getCustomerName(),
        sale.getSubtotalAmount(),
        sale.getDiscountAmount(),
        sale.getTaxAmount(),
        sale.getTotalAmount(),
        sale.getStatus(),
        sale.getSoldAt(),
        lines.stream().map(line -> SaleLineResponse.from(line, includeCostPrice)).toList(),
        payments.stream().map(SalePaymentResponse::from).toList());
  }
}
