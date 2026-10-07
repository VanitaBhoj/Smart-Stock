package com.smartstock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateInventoryRequest(
        @NotNull(message = "productId is required")
        Long productId,

        @NotNull(message = "availableQuantity is required")
        @PositiveOrZero(message = "availableQuantity cannot be negative")
        Long availableQuantity,

        @PositiveOrZero(message = "reservedQuantity cannot be negative")
        Long reservedQuantity
) {
}
