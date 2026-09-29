package com.company.fashionpos.hr;

import java.util.List;

public record AttendancePageResponse(
    List<AttendanceResponse> items, int total, AttendanceSummaryResponse summary) {}
