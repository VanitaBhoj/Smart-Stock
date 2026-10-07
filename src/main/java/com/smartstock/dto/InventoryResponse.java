package com.smartstock.dto;

import java.time.Instant;

public record InventoryResponse(
        Long id,
        Long productId,
        long availableQuantity,
        long reservedQuantity,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
