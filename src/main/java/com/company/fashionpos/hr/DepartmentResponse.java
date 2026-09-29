package com.company.fashionpos.hr;

import java.util.UUID;

public record DepartmentResponse(
    UUID id,
    String name,
    String code,
    String description,
    UUID branchId,
    String branchName,
    UUID managerUserId,
    String managerUserDisplayName,
    boolean active) {

  public static DepartmentResponse from(Department department) {
    return new DepartmentResponse(
        department.getId(),
        department.getName(),
        department.getCode(),
        department.getDescription(),
        department.getBranch() == null ? null : department.getBranch().getId(),
        department.getBranch() == null ? null : department.getBranch().getName(),
        department.getManagerUser() == null ? null : department.getManagerUser().getId(),
        department.getManagerUser() == null ? null : department.getManagerUser().getDisplayName(),
        department.isActive());
  }
}
