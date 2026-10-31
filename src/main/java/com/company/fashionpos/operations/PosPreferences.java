package com.company.fashionpos.operations;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record PosPreferences(
    boolean dailySummaryEnabled,
    boolean inAppEnabled,
    boolean emailEnabled,
    @NotNull @Size(max = 100) List<UUID> recipientIds,
    @Min(1) @Max(525600) int inactivityMinutes,
    @Min(1) @Max(480) int longEntryMinutes,
    @NotBlank @Size(max = 64) String timeZone) {}
