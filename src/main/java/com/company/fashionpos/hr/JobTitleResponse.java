package com.company.fashionpos.hr;

import java.util.UUID;

public record JobTitleResponse(
    UUID id,
    String title,
    String code,
    String description,
    UUID departmentId,
    String departmentName,
    boolean active) {

  public static JobTitleResponse from(JobTitle jobTitle) {
    return new JobTitleResponse(
        jobTitle.getId(),
        jobTitle.getTitle(),
        jobTitle.getCode(),
        jobTitle.getDescription(),
        jobTitle.getDepartment().getId(),
        jobTitle.getDepartment().getName(),
        jobTitle.isActive());
  }
}
