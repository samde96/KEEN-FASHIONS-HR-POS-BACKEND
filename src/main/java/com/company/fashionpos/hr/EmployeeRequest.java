package com.company.fashionpos.hr;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeRequest(
    @NotBlank @Size(max = 40) String employeeNumber,
    @NotBlank @Size(max = 80) String firstName,
    @Size(max = 80) String middleName,
    @NotBlank @Size(max = 80) String lastName,
    @Size(max = 80) String preferredName,
    @Size(max = 254) String email,
    @Size(max = 40) String phone,
    @Size(max = 32) String gender,
    LocalDate dateOfBirth,
    @Size(max = 80) String nationalIdNumber,
    @NotNull UUID primaryBranchId,
    UUID departmentId,
    UUID jobTitleId,
    @NotNull EmployeeEmploymentType employmentType,
    @NotNull EmployeeEmploymentStatus employmentStatus,
    @NotNull LocalDate joiningDate,
    LocalDate probationEndDate,
    LocalDate contractStartDate,
    LocalDate contractEndDate,
    UUID userId,
    UUID managerEmployeeId,
    @Size(max = 160) String workLocation,
    @Size(max = 120) String emergencyContactName,
    @Size(max = 40) String emergencyContactPhone,
    @Size(max = 500) String address,
    @Size(max = 2000) String notes,
    @DecimalMin("0.00") BigDecimal basicSalary,
    SalaryPaymentMethod salaryPaymentMethod,
    @Size(max = 120) String bankName,
    @Size(max = 80) String bankAccountNumber,
    @Size(max = 160) String bankAccountName,
    @Size(max = 40) String mpesaNumber,
    Boolean active) {}
