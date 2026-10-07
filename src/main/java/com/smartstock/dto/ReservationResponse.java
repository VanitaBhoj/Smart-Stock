package com.smartstock.dto;

import com.smartstock.entity.ReservationStatus;
import java.time.Instant;

public record ReservationResponse(
        Long id,
        Long productId,
        long quantity,
        ReservationStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}
