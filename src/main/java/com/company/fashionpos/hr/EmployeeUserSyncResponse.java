package com.company.fashionpos.hr;

import java.util.List;

public record EmployeeUserSyncResponse(
    int totalUsers, int createdCount, int skippedCount, List<EmployeeListResponse> employees) {}
