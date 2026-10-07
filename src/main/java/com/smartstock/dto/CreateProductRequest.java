package com.smartstock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "sku is required")
        String sku,

        String description,

        @NotNull(message = "price is required")
        @Positive(message = "price must be positive")
        BigDecimal price,

        String category,

        Boolean active
) {
}
