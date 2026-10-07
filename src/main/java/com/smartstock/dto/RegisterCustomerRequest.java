package com.smartstock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCustomerRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Size(min = 12, max = 72) String password
) {
}
