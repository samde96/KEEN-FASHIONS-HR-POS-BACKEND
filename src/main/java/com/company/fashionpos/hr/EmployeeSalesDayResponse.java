package com.company.fashionpos.hr;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeSalesDayResponse(LocalDate salesDate, BigDecimal amount, int receiptCount) {}
