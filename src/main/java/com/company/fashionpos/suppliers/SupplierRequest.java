package com.company.fashionpos.suppliers;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 160) String contactPerson,
    @Size(max = 40) String phone,
    @Email @Size(max = 254) String email,
    @Size(max = 500) String notes,
    SupplierStatus status) {}
