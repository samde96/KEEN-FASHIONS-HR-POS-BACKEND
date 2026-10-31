package com.company.fashionpos.suppliers;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SupplierRequest(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 40) String supplierCode,
    @Size(max = 160) String companyName,
    @Size(max = 160) String contactPerson,
    @Size(max = 40) String phone,
    @Email @Size(max = 254) String email,
    @Size(max = 255) String address,
    @Size(max = 40) String kraPin,
    @Size(max = 120) String paymentTerms,
    @DecimalMin("0.00") BigDecimal creditLimit,
    @Size(max = 500) String bankDetails,
    @Size(max = 500) String productsSupplied,
    @DecimalMin("0.00") BigDecimal outstandingBalance,
    @Size(max = 500) String notes,
    SupplierStatus status) {}
