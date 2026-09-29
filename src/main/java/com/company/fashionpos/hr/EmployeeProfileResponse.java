package com.company.fashionpos.hr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EmployeeProfileResponse(
    UUID id,
    String employeeNumber,
    String firstName,
    String middleName,
    String lastName,
    String fullName,
    String preferredName,
    String email,
    String phone,
    String gender,
    LocalDate dateOfBirth,
    String nationalIdNumber,
    UUID branchId,
    String branchName,
    UUID departmentId,
    String departmentName,
    UUID jobTitleId,
    String jobTitleTitle,
    EmployeeEmploymentType employmentType,
    EmployeeEmploymentStatus employmentStatus,
    LocalDate joiningDate,
    LocalDate probationEndDate,
    LocalDate contractStartDate,
    LocalDate contractEndDate,
    UUID linkedUserId,
    String linkedUserDisplayName,
    UUID managerEmployeeId,
    String managerEmployeeName,
    String workLocation,
    String emergencyContactName,
    String emergencyContactPhone,
    String address,
    String notes,
    BigDecimal basicSalary,
    SalaryPaymentMethod salaryPaymentMethod,
    String bankName,
    String bankAccountNumber,
    String bankAccountName,
    String mpesaNumber,
    boolean active,
    List<EmployeeProfileTab> tabs) {

  public static EmployeeProfileResponse from(Employee employee) {
    return new EmployeeProfileResponse(
        employee.getId(),
        employee.getEmployeeNumber(),
        employee.getFirstName(),
        employee.getMiddleName(),
        employee.getLastName(),
        fullName(employee),
        employee.getPreferredName(),
        employee.getEmail(),
        employee.getPhone(),
        employee.getGender(),
        employee.getDateOfBirth(),
        employee.getNationalIdNumber(),
        employee.getPrimaryBranch().getId(),
        employee.getPrimaryBranch().getName(),
        employee.getDepartment() == null ? null : employee.getDepartment().getId(),
        employee.getDepartment() == null ? null : employee.getDepartment().getName(),
        employee.getJobTitle() == null ? null : employee.getJobTitle().getId(),
        employee.getJobTitle() == null ? null : employee.getJobTitle().getTitle(),
        employee.getEmploymentType(),
        employee.getEmploymentStatus(),
        employee.getJoiningDate(),
        employee.getProbationEndDate(),
        employee.getContractStartDate(),
        employee.getContractEndDate(),
        employee.getUser() == null ? null : employee.getUser().getId(),
        employee.getUser() == null ? null : employee.getUser().getDisplayName(),
        employee.getManager() == null ? null : employee.getManager().getId(),
        employee.getManager() == null ? null : fullName(employee.getManager()),
        employee.getWorkLocation(),
        employee.getEmergencyContactName(),
        employee.getEmergencyContactPhone(),
        employee.getAddress(),
        employee.getNotes(),
        employee.getBasicSalary(),
        employee.getSalaryPaymentMethod(),
        employee.getBankName(),
        employee.getBankAccountNumber(),
        employee.getBankAccountName(),
        employee.getMpesaNumber(),
        employee.isActive(),
        List.of(
            new EmployeeProfileTab("overview", "Overview"),
            new EmployeeProfileTab("employment", "Employment"),
            new EmployeeProfileTab("attendance", "Attendance"),
            new EmployeeProfileTab("leave", "Leave"),
            new EmployeeProfileTab("payroll", "Payroll"),
            new EmployeeProfileTab("performance", "Performance"),
            new EmployeeProfileTab("documents", "Documents"),
            new EmployeeProfileTab("history", "History")));
  }

  public static String fullName(Employee employee) {
    StringBuilder fullName = new StringBuilder(employee.getFirstName());
    if (employee.getMiddleName() != null && !employee.getMiddleName().isBlank()) {
      fullName.append(' ').append(employee.getMiddleName());
    }
    fullName.append(' ').append(employee.getLastName());
    return fullName.toString();
  }
}
