package com.company.fashionpos.modules;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String notificationType,
    String title,
    String message,
    String sourceModule,
    UUID sourceRecordId,
    String status,
    Instant createdAt,
    Instant readAt) {}
