package com.company.fashionpos.hr;

import java.util.List;

public record LeavePageResponse(
    List<LeaveTypeResponse> leaveTypes,
    List<LeaveBalanceResponse> balances,
    List<LeaveRequestResponse> requests) {}
