package com.company.fashionpos.hr;

public record AttendanceSummaryResponse(
    int totalRecords,
    int presentCount,
    int absentCount,
    int lateCount,
    int leaveCount,
    int halfDayCount,
    int holidayCount,
    int offDayCount,
    int correctedCount,
    long totalWorkedMinutes,
    long totalOvertimeMinutes) {}
