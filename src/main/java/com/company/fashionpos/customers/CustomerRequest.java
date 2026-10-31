package com.company.fashionpos.customers;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CustomerRequest(
    UUID branchId,
    @NotBlank @Size(max = 40) String customerCode,
    @NotBlank @Size(max = 160) String name,
    @Size(max = 160) String contactPerson,
    @Size(max = 40) String phone,
    @Email @Size(max = 254) String email,
    @Size(max = 255) String address,
    @Size(max = 40) String kraPin,
    @NotNull CustomerType customerType,
    @DecimalMin("0.00") BigDecimal creditLimit,
    @DecimalMin("0.00") BigDecimal outstandingBalance,
    @Size(max = 120) String paymentTerms,
    @Size(max = 500) String notes,
    @NotNull CustomerStatus status) {}
