package com.smartstock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "items must contain at least one product")
        List<@Valid OrderItemRequest> items
) {
}
