package com.company.fashionpos.hr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeListResponse(
    UUID id,
    String employeeNumber,
    String fullName,
    String preferredName,
    String email,
    String phone,
    UUID branchId,
    String branchName,
    UUID departmentId,
    String departmentName,
    UUID jobTitleId,
    String jobTitleTitle,
    EmployeeEmploymentType employmentType,
    EmployeeEmploymentStatus employmentStatus,
    LocalDate joiningDate,
    BigDecimal basicSalary,
    SalaryPaymentMethod salaryPaymentMethod,
    boolean active,
    UUID linkedUserId,
    String linkedUserDisplayName) {

  public static EmployeeListResponse from(Employee employee) {
    return new EmployeeListResponse(
        employee.getId(),
        employee.getEmployeeNumber(),
        EmployeeProfileResponse.fullName(employee),
        employee.getPreferredName(),
        employee.getEmail(),
        employee.getPhone(),
        employee.getPrimaryBranch().getId(),
        employee.getPrimaryBranch().getName(),
        employee.getDepartment() == null ? null : employee.getDepartment().getId(),
        employee.getDepartment() == null ? null : employee.getDepartment().getName(),
        employee.getJobTitle() == null ? null : employee.getJobTitle().getId(),
        employee.getJobTitle() == null ? null : employee.getJobTitle().getTitle(),
        employee.getEmploymentType(),
        employee.getEmploymentStatus(),
        employee.getJoiningDate(),
        employee.getBasicSalary(),
        employee.getSalaryPaymentMethod(),
        employee.isActive(),
        employee.getUser() == null ? null : employee.getUser().getId(),
        employee.getUser() == null ? null : employee.getUser().getDisplayName());
  }
}
