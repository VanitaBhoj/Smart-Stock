package com.smartstock.controller;

import com.smartstock.dto.AdjustInventoryRequest;
import com.smartstock.dto.CreateInventoryRequest;
import com.smartstock.dto.InventoryResponse;
import com.smartstock.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryResponse create(@Valid @RequestBody CreateInventoryRequest request) {
        return inventoryService.create(request);
    }

    @GetMapping("/{productId}")
    public InventoryResponse findByProductId(@PathVariable Long productId) {
        return inventoryService.findByProductId(productId);
    }

    @PutMapping("/{productId}/adjust")
    public InventoryResponse adjust(
            @PathVariable Long productId,
            @Valid @RequestBody AdjustInventoryRequest request
    ) {
        return inventoryService.adjust(productId, request);
    }

    @PutMapping("/{productId}/add-stock")
    public InventoryResponse addStock(
            @PathVariable Long productId,
            @Valid @RequestBody AdjustInventoryRequest request
    ) {
        return inventoryService.addStock(productId, request);
    }

    @PutMapping("/{productId}/remove-stock")
    public InventoryResponse removeStock(
            @PathVariable Long productId,
            @Valid @RequestBody AdjustInventoryRequest request
    ) {
        return inventoryService.removeStock(productId, request);
    }
}
