package com.smartstock.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        long quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
