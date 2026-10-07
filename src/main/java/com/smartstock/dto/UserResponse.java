package com.smartstock.dto;

import com.smartstock.entity.UserRole;

public record UserResponse(Long id, String username, UserRole role) {
}
