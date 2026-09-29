package com.company.fashionpos.branch;

import java.util.UUID;

public record BranchResponse(
    UUID id, String name, String code, String timeZone, BranchStatus status) {

  public static BranchResponse from(Branch branch) {
    return new BranchResponse(
        branch.getId(),
        branch.getName(),
        branch.getCode(),
        branch.getTimeZone(),
        branch.getStatus());
  }
}
