package com.smartstock.service;

import java.time.Instant;

public record OutboxBusinessEvent(
        Long id,
        String eventType,
        String aggregateType,
        Long aggregateId,
        String payload,
        Instant createdAt
) {
}
