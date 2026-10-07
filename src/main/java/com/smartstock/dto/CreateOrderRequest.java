package com.smartstock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CreateOrderRequest(
        @NotNull(message = "customerId is required")
        @Positive(message = "customerId must be positive")
        Long customerId,
        @NotEmpty(message = "items must contain at least one product")
        List<@Valid OrderItemRequest> items
) {
}
