package com.company.fashionpos.suppliers;

import java.math.BigDecimal;
import java.util.UUID;

public record SupplierResponse(
    UUID id,
    String name,
    String supplierCode,
    String companyName,
    String contactPerson,
    String phone,
    String email,
    String address,
    String kraPin,
    String paymentTerms,
    BigDecimal creditLimit,
    String bankDetails,
    String productsSupplied,
    BigDecimal outstandingBalance,
    String notes,
    SupplierStatus status) {

  public static SupplierResponse from(Supplier supplier) {
    return new SupplierResponse(
        supplier.getId(),
        supplier.getName(),
        supplier.getSupplierCode(),
        supplier.getCompanyName(),
        supplier.getContactPerson(),
        supplier.getPhone(),
        supplier.getEmail(),
        supplier.getAddress(),
        supplier.getKraPin(),
        supplier.getPaymentTerms(),
        supplier.getCreditLimit(),
        supplier.getBankDetails(),
        supplier.getProductsSupplied(),
        supplier.getOutstandingBalance(),
        supplier.getNotes(),
        supplier.getStatus());
  }
}
