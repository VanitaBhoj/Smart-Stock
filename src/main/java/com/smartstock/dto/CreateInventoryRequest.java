package com.smartstock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;

public record CreateInventoryRequest(
        @NotNull(message = "productId is required")
        @Positive(message = "productId must be positive")
        Long productId,

        @NotNull(message = "availableQuantity is required")
        @PositiveOrZero(message = "availableQuantity cannot be negative")
        Long availableQuantity
) {
}
