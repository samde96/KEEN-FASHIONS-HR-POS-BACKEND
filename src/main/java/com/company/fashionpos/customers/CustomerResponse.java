package com.company.fashionpos.customers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
    UUID id,
    UUID branchId,
    String branchName,
    String customerCode,
    String name,
    String contactPerson,
    String phone,
    String email,
    String address,
    String kraPin,
    CustomerType customerType,
    BigDecimal creditLimit,
    BigDecimal outstandingBalance,
    String paymentTerms,
    String notes,
    CustomerStatus status,
    Instant createdAt,
    Instant updatedAt) {

  public static CustomerResponse from(Customer customer) {
    return new CustomerResponse(
        customer.getId(),
        customer.getBranch() == null ? null : customer.getBranch().getId(),
        customer.getBranch() == null ? null : customer.getBranch().getName(),
        customer.getCustomerCode(),
        customer.getName(),
        customer.getContactPerson(),
        customer.getPhone(),
        customer.getEmail(),
        customer.getAddress(),
        customer.getKraPin(),
        customer.getCustomerType(),
        customer.getCreditLimit(),
        customer.getOutstandingBalance(),
        customer.getPaymentTerms(),
        customer.getNotes(),
        customer.getStatus(),
        customer.getCreatedAt(),
        customer.getUpdatedAt());
  }
}
