package com.company.fashionpos.hr;

import java.time.Instant;
import java.util.UUID;

public record EmployeeDocumentResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    String employeeNumber,
    UUID branchId,
    String branchName,
    String documentType,
    String title,
    String fileName,
    String contentType,
    long sizeBytes,
    String notes,
    UUID uploadedByUserId,
    String uploadedByUserDisplayName,
    Instant uploadedAt) {

  public static EmployeeDocumentResponse from(EmployeeDocument document) {
    Employee employee = document.getEmployee();
    return new EmployeeDocumentResponse(
        document.getId(),
        employee.getId(),
        EmployeeProfileResponse.fullName(employee),
        employee.getEmployeeNumber(),
        employee.getPrimaryBranch().getId(),
        employee.getPrimaryBranch().getName(),
        document.getDocumentType(),
        document.getTitle(),
        document.getFileName(),
        document.getContentType(),
        document.getSizeBytes(),
        document.getNotes(),
        document.getUploadedByUser() == null ? null : document.getUploadedByUser().getId(),
        document.getUploadedByUser() == null ? null : document.getUploadedByUser().getDisplayName(),
        document.getUploadedAt());
  }
}
