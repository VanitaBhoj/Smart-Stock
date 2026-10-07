package com.smartstock.controller;

import com.smartstock.dto.RegisterCustomerRequest;
import com.smartstock.dto.UserResponse;
import com.smartstock.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register customer", description = "Creates a CUSTOMER account. Admin accounts are provisioned from deployment configuration.", security = {})
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        UserResponse user = userService.registerCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
}
