package com.company.fashionpos.modules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ModuleWorkItem(
    UUID id,
    String reference,
    String title,
    String status,
    BigDecimal amount,
    String branchName,
    LocalDate dueDate,
    Instant createdAt) {}
