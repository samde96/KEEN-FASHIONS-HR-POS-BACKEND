package com.company.fashionpos.modules;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
    UUID id,
    UUID branchId,
    String branchName,
    UUID actorUserId,
    String actorName,
    String action,
    String targetType,
    UUID targetId,
    String reason,
    String correlationId,
    String metadataJson,
    Instant createdAt) {}
