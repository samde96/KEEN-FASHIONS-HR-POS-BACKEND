package com.company.fashionpos.hr;

import java.util.List;

public record EmployeePageResponse(List<EmployeeListResponse> items, long total) {}
