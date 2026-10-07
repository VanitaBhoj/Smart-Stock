package com.smartstock.dto;

import jakarta.validation.constraints.NotNull;

public record AdjustInventoryRequest(
        @NotNull(message = "quantity is required")
        Long quantity
) {
}
