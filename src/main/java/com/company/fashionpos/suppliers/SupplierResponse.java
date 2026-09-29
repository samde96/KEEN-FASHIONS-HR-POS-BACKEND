package com.company.fashionpos.suppliers;

import java.util.UUID;

public record SupplierResponse(
    UUID id,
    String name,
    String contactPerson,
    String phone,
    String email,
    String notes,
    SupplierStatus status) {

  public static SupplierResponse from(Supplier supplier) {
    return new SupplierResponse(
        supplier.getId(),
        supplier.getName(),
        supplier.getContactPerson(),
        supplier.getPhone(),
        supplier.getEmail(),
        supplier.getNotes(),
        supplier.getStatus());
  }
}
